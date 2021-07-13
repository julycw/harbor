package julyww.harbor.core

import com.github.dockerjava.api.DockerClient
import julyww.harbor.common.MD5Util
import julyww.harbor.common.PageResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.apache.commons.codec.digest.Md5Crypt
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.StringUtils
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.exchange
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import kotlin.io.path.readBytes

@Entity
class AppEntity(
    @Id
    @GeneratedValue
    var id: Long?,

    @Column
    var name: String,

    @Column
    var containerId: String?,

    @Column
    var md5: String?,

    @Column
    var downloadAppUrl: String?,

    @Column
    var localAppPath: String?,

    @Column
    var basicAuthUsername: String?,

    @Column
    var basicAuthPassword: String?,

    @Column
    var version: String?,

    @Column
    var latestUpdateTime: Date?
)

interface AppRepository : JpaRepository<AppEntity, Long>

val appUpdateState: MutableSet<Long> = Collections.synchronizedSet(mutableSetOf())

@Service
class AppManageService(
    private val appRepository: AppRepository,
    private val dockerClient: DockerClient
) {

    private val restTemplate = RestTemplate()

    fun list(): PageResult<AppEntity> {
        val list = appRepository.findAll()
        return PageResult(
            list = list,
            total = list.size
        )
    }

    fun save(entity: AppEntity): Long? {
        return if (entity.id == null) {
            appRepository.save(entity).id
        } else {
            appRepository.findByIdOrNull(entity.id!!)?.let {
                it.name = entity.name
                it.containerId = entity.containerId
                it.downloadAppUrl = entity.downloadAppUrl
                it.localAppPath = entity.localAppPath
                it.basicAuthUsername = entity.basicAuthUsername
                it.basicAuthPassword = entity.basicAuthPassword
                appRepository.save(it)
            }
            entity.id
        }
    }

    fun delete(id: Long) {
        appRepository.deleteById(id)
    }

    fun start(id: Long) {
        appRepository.findByIdOrNull(id)?.let {
            it.containerId?.let { containerId ->
                if (containerId.isNotBlank()) {
                    dockerClient.startContainerCmd(containerId).exec()
                }
            }
        }
    }

    fun stop(id: Long) {
        appRepository.findByIdOrNull(id)?.let {
            it.containerId?.let { containerId ->
                if (containerId.isNotBlank()) {
                    dockerClient.stopContainerCmd(containerId).exec()
                }
            }
        }
    }

    fun restart(id: Long) {
        appRepository.findByIdOrNull(id)?.let {
            it.containerId?.let { containerId ->
                if (containerId.isNotBlank()) {
                    dockerClient.restartContainerCmd(containerId).exec()
                }
            }
        }
    }

    fun update(id: Long) {
        appRepository.findByIdOrNull(id)?.let {
            val downloadUrl = it.downloadAppUrl ?: throw error("未设定下载地址")
            val localPath = it.localAppPath ?: throw error("未设定部署地址")
            val httpRequest: HttpEntity<Void> =
                HttpEntity(basicAuth(it.basicAuthUsername ?: "", it.basicAuthPassword ?: ""))

            if (appUpdateState.contains(id)) {
                throw error("正在更新，请勿重复操作")
            }
            appUpdateState.add(id)
            GlobalScope.launch { // 在后台启动一个新的协程并继续
                try {
                    val asyncForDownloadFile = async(context = Dispatchers.IO) {
                        val response: ResponseEntity<ByteArray> = restTemplate.exchange(
                            downloadUrl,
                            HttpMethod.GET,
                            httpRequest,
                            ByteArray::class
                        )
                        it.md5 = MD5Util.md5(response.body!!)
                        if (downloadUrl.endsWith(".tar")) {
                            tarUnarchive(response.body!!, localPath)
                        } else {
                            Files.write(
                                Path.of(localPath),
                                response.body!!,
                                StandardOpenOption.WRITE,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.TRUNCATE_EXISTING
                            )
                        }
                        it.latestUpdateTime = Date()
                    }
                    asyncForDownloadFile.await()
                    restart(id)
                    appRepository.save(it)
                } finally {
                    appUpdateState.remove(id)
                }
            }
        }
    }

    fun basicAuth(username: String, password: String): HttpHeaders {
        return object : HttpHeaders() {
            init {
                val auth = "$username:$password"
                val encodedAuth: ByteArray = Base64.getEncoder().encode(auth.toByteArray())
                val authHeader = "Basic " + String(encodedAuth)
                set("Authorization", authHeader)
            }
        }
    }

    fun tarUnarchive(file: ByteArray, outputDir: String) {
        ByteArrayInputStream(file).use { fileInputStream ->
            val tarArchiveInputStream = TarArchiveInputStream(fileInputStream)
            var entry: TarArchiveEntry? = null
            while (tarArchiveInputStream.nextTarEntry?.also { entry = it } != null) {
                if (entry!!.isDirectory) {
                    continue
                }
                val outputFile = File(Paths.get(outputDir, entry!!.name).toUri())
                if (!outputFile.parentFile.exists()) {
                    outputFile.parentFile.mkdirs()
                }
                Files.write(
                    outputFile.toPath(),
                    tarArchiveInputStream.readAllBytes(),
                    StandardOpenOption.WRITE,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
                )
            }
        }
    }

}