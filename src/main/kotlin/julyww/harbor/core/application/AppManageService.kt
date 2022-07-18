package julyww.harbor.core.application

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.Frame
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import julyww.harbor.common.MD5Util
import julyww.harbor.common.PageResult
import julyww.harbor.core.container.ContainerService
import julyww.harbor.persist.IdGenerator
import julyww.harbor.persist.app.AppEntity
import julyww.harbor.persist.app.AppRepository
import julyww.harbor.utils.CommonUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.exchange
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.*


val appUpdateState: MutableSet<Long> = Collections.synchronizedSet(mutableSetOf())

data class AppDTO(
    var id: Long?,
    var name: String,
    var containerId: String?,
    var md5: String?,
    var downloadAppUrl: String?,
    var localAppPath: String?,
    var basicAuthUsername: String?,
    var basicAuthPassword: String?,
    var version: String?,
    var latestUpdateTime: Date?,
    var remoteMd5: String?,
    var autoRestart: Boolean?
)

@ApiModel
class AppQueryBean {

    @ApiModelProperty("是否基于container是否存在来进行过滤")
    var filterByContainerExist: Boolean = true
}

@Service
class AppManageService(
    private val appRepository: AppRepository,
    private val dockerClient: DockerClient,
    private val containerService: ContainerService,
    private val idGenerator: IdGenerator
) {

    private val restTemplate = let {
        val factory = SimpleClientHttpRequestFactory()
        factory.setConnectTimeout(3000)
        factory.setReadTimeout(2 * 60 * 1000)
        RestTemplate(factory)
    }

    fun list(query: AppQueryBean): PageResult<AppDTO> {
        var list = appRepository.findAll()
        if (query.filterByContainerExist) {
            val containers = containerService.list().map { it.id }.toSet()
            list = list.filter { it.containerId.isNullOrBlank() || containers.contains(it.containerId) }
        }
        return PageResult(
            list = list.map {
                AppDTO(
                    id = it.id,
                    name = it.name,
                    containerId = it.containerId,
                    md5 = it.md5,
                    downloadAppUrl = it.downloadAppUrl,
                    localAppPath = it.localAppPath,
                    basicAuthUsername = it.basicAuthUsername,
                    basicAuthPassword = it.basicAuthPassword,
                    version = it.version,
                    latestUpdateTime = it.latestUpdateTime,
                    autoRestart = it.autoRestart,
                    remoteMd5 = remoteMd5(it),
                )
            },
            total = appRepository.count()
        )
    }

    fun save(entity: AppEntity): Long? {
        val targetId = entity.id
        return if (targetId == null) {
            entity.id = idGenerator.next()
            appRepository.save(entity).id
        } else {
            appRepository.findByIdOrNull(targetId)?.let {
                it.name = entity.name
                it.containerId = entity.containerId
                it.downloadAppUrl = entity.downloadAppUrl
                it.localAppPath = entity.localAppPath
                it.basicAuthUsername = entity.basicAuthUsername
                it.basicAuthPassword = entity.basicAuthPassword
                it.autoRestart = entity.autoRestart
                appRepository.save(it)
            } ?: let {
                entity.id = idGenerator.next()
                appRepository.save(entity).id
            }
            entity.id
        }
    }

    fun delete(id: Long) {
        appRepository.deleteById(id)
    }

    fun log(id: Long, tail: Int = 500, since: Date? = null, withTimestamps: Boolean = false): List<String> {
        val logs: MutableList<String> = mutableListOf()
        appRepository.findByIdOrNull(id)?.let {
            it.containerId?.let { containerId ->
                if (containerId.isNotBlank()) {
                    var cmd = dockerClient.logContainerCmd(containerId)
                        .withTail(tail)
                        .withStdOut(true)
                        .withStdErr(true)
                    since?.let {
                        cmd = cmd.withSince((since.time / 1000).toInt())
                    }
                    if (withTimestamps) {
                        cmd = cmd.withTimestamps(true)
                    }
                    try {
                        cmd.exec(object : ResultCallback.Adapter<Frame>() {
                            override fun onNext(frame: Frame) {
                                logs.add(frame.toString())
                            }
                        }).awaitCompletion()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
        return logs
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
            val downloadUrl = it.downloadAppUrl ?: error("未设定下载地址")
            val localPath = it.localAppPath ?: error("未设定部署地址")
            val httpRequest: HttpEntity<Void> =
                HttpEntity(CommonUtils.basicAuth(it.basicAuthUsername ?: "", it.basicAuthPassword ?: ""))

            if (appUpdateState.contains(id)) {
                error("正在更新，请勿重复操作")
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
                            CommonUtils.tarUnarchive(response.body!!, localPath)
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
                    appRepository.save(it)
                    if (it.autoRestart) {
                        restart(id)
                    }
                } finally {
                    appUpdateState.remove(id)
                }
            }
        }
    }

    fun remoteMd5(appEntity: AppEntity): String? {
        val downloadUrl = appEntity.downloadAppUrl ?: return null
        val md5Url = "$downloadUrl.md5"
        val httpRequest: HttpEntity<Void> =
            HttpEntity(CommonUtils.basicAuth(appEntity.basicAuthUsername ?: "", appEntity.basicAuthPassword ?: ""))
        return try {
            val response: ResponseEntity<String> = restTemplate.exchange(
                md5Url,
                HttpMethod.GET,
                httpRequest,
                String::class
            )
            response.body?.let {
                if (it.length >= 32) it.substring(0, 32) else null
            }
        } catch (e: Exception) {
            null
        }
    }

}