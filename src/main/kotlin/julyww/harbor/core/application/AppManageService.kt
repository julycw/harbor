package julyww.harbor.core.application

import cn.hutool.crypto.SecureUtil
import cn.trustway.nb.common.auth.exception.app.AppException
import cn.trustway.nb.util.SSLUtil
import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.Frame
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import julyww.harbor.common.PageResult
import julyww.harbor.core.certification.CertificationService
import julyww.harbor.core.container.ContainerService
import julyww.harbor.persist.IdGenerator
import julyww.harbor.persist.app.AppEntity
import julyww.harbor.persist.app.AppRepository
import julyww.harbor.utils.CommonUtils
import julyww.harbor.utils.LockUtils
import org.apache.http.impl.client.HttpClients
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.exchange
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.concurrent.Executors

data class AppDTO(
    var id: Long?,
    var name: String,
    var containerId: String?,
    var md5: String?,
    var downloadAppUrl: String?,
    var localAppPath: String?,
    var certificationId: String?,
    var basicAuthUsername: String?,
    var basicAuthPassword: String?,
    var version: String?,
    var latestUpdateTime: Date?,
    var remoteMd5: String?,
    var autoRestart: Boolean?,
    var checkMd5: Boolean?,
    var scheduleRestart: Boolean?,
    var restartAt: String?,
    var scheduleUpdate: Boolean?,
    var updateAt: String?,
)

data class BasicAuth(
    val username: String,
    val password: String
)

@ApiModel
class AppQueryBean(

    @ApiModelProperty("是否基于container是否存在来进行过滤")
    var filterByContainerExist: Boolean = false,

    @ApiModelProperty("是否获取远端md5")
    var withRemoteMd5: Boolean = true
)

@Service
class AppManageService(
    private val appRepository: AppRepository,
    private val dockerClient: DockerClient,
    private val containerService: ContainerService,
    private val idGenerator: IdGenerator,
    private val certificationService: CertificationService
) {

    private val log = LoggerFactory.getLogger(AppManageService::class.java)

    private val downloadRestTemplate = let {
        val factory = HttpComponentsClientHttpRequestFactory()
        val httpClient = HttpClients.custom()
            .setSSLContext(SSLUtil.sslContextNoCheck())
            .setSSLHostnameVerifier(SSLUtil::hostnameVerifier)
            .build()
        factory.httpClient = httpClient
        factory.setConnectTimeout(1000)
        factory.setReadTimeout(2 * 60 * 1000)
        RestTemplate(factory)
    }

    private val restTemplate = let {
        val factory = HttpComponentsClientHttpRequestFactory()
        val httpClient = HttpClients.custom()
            .setSSLContext(SSLUtil.sslContextNoCheck())
            .setSSLHostnameVerifier(SSLUtil::hostnameVerifier)
            .build()
        factory.httpClient = httpClient
        factory.setConnectTimeout(1000)
        factory.setReadTimeout(1000)
        RestTemplate(factory)
    }

    private val executors = Executors.newCachedThreadPool()

    fun list(query: AppQueryBean): PageResult<AppDTO> {
        var list = appRepository.findAll().toList()
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
                    md5 = it.md5 ?: localMd5(it),
                    downloadAppUrl = it.downloadAppUrl,
                    localAppPath = it.localAppPath,
                    certificationId = it.certificationId,
                    basicAuthUsername = it.basicAuthUsername,
                    basicAuthPassword = it.basicAuthPassword,
                    version = it.version,
                    latestUpdateTime = it.latestUpdateTime,
                    autoRestart = it.autoRestart,
                    remoteMd5 = if (query.withRemoteMd5) remoteMd5(it) else null,
                    checkMd5 = it.checkMd5,
                    scheduleRestart = it.scheduleRestart,
                    restartAt = it.restartAt,
                    scheduleUpdate = it.scheduleUpdate,
                    updateAt = it.updateAt,
                )
            },
            total = list.size.toLong()
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
                it.certificationId = entity.certificationId
                it.basicAuthUsername = entity.basicAuthUsername
                it.basicAuthPassword = entity.basicAuthPassword
                it.autoRestart = entity.autoRestart
                it.checkMd5 = entity.checkMd5
                it.scheduleRestart = entity.scheduleRestart
                it.restartAt = entity.restartAt
                it.scheduleUpdate = entity.scheduleUpdate
                it.updateAt = entity.updateAt
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
                    log.info("Restarting ${it.name}...")
                    try {
                        dockerClient.restartContainerCmd(containerId).exec()
                        log.info("Restart ${it.name} finish!")
                    } catch (e: Exception) {
                        log.info("Restart ${it.name} failed: {}", e.message)
                        throw e
                    }
                }
            }
        }
    }

    fun update(id: Long, autoSkip: Boolean = false) {
        LockUtils.check(id)
        val app = appRepository.findByIdOrNull(id) ?: error("应用不存在")
        val downloadUrl = app.downloadAppUrl ?: error("未设定下载地址")
        val localPath = app.localAppPath ?: error("未设定部署地址")
        executors.submit {
            LockUtils.lock(id) {
                appRepository.findByIdOrNull(id)?.let {
                    val remoteMd5 = remoteMd5(it)
                    if (autoSkip && remoteMd5 != null && remoteMd5 == it.md5) {
                        return@let
                    }

                    log.info("Updating ${it.name}...")

                    try {
                        val (username, password) = getBasicAuth(it)
                        val httpRequest: HttpEntity<Void> = HttpEntity(CommonUtils.basicAuth(username, password))

                        log.info("Updating ${it.name}, begin download...")
                        val response: ResponseEntity<ByteArray> = downloadRestTemplate.exchange(
                            downloadUrl,
                            HttpMethod.GET,
                            httpRequest,
                            ByteArray::class
                        )
                        log.info("Updating ${it.name}, download success")
                        it.md5 = SecureUtil.md5().digestHex(response.body)
                        if (it.checkMd5 == true) {
                            if (remoteMd5 != it.md5) {
                                error("MD5校验失败，请重试")
                            }
                        }

                        if (it.autoRestart && !localPath.endsWith("harbor.jar")) {
                            try {
                                stop(id)
                            } catch (ignore: Exception) {
                            }
                        }

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
                        appRepository.save(it)
                        log.info("Updating ${it.name} finish")

                        if (it.autoRestart) {
                            restart(id)
                        }

                    } catch (e: Exception) {
                        log.error("Updating ${it.name} failed: {}", e.message)
                    }
                }
            }
        }
    }

    fun updateByUploadFile(id: Long, file: MultipartFile) {
        LockUtils.check(id)
        val app = appRepository.findByIdOrNull(id) ?: error("应用不存在")
        val localPath = app.localAppPath ?: error("未设定部署地址")
        executors.submit {
            LockUtils.lock(id) {
                appRepository.findByIdOrNull(id)?.let {
                    log.info("Updating ${it.name}...")

                    try {

                        if (it.autoRestart && !localPath.endsWith("harbor.jar")) {
                            try {
                                stop(id)
                            } catch (ignore: Exception) {
                            }
                        }

                        if (file.originalFilename!!.endsWith(".tar")) {
                            CommonUtils.tarUnarchive(file.bytes, localPath)
                        } else {
                            file.transferTo(Path.of(localPath))
                        }
                        it.latestUpdateTime = Date()
                        appRepository.save(it)
                        log.info("Updating ${it.name} finish")

                        if (it.autoRestart) {
                            restart(id)
                        }
                    } catch (e: Exception) {
                        log.error("Updating ${it.name} failed: {}", e.message)
                        throw e
                    }
                }
            }
        }
    }

    fun localMd5(appEntity: AppEntity): String? {
        return try {
            appEntity.localAppPath?.let {
                val md5 = SecureUtil.md5(Path.of(it).toFile())
                appEntity.md5 = md5
                appRepository.save(appEntity)
                md5
            }
        } catch (e: Exception) {
            null
        }
    }

    fun remoteMd5(appEntity: AppEntity): String? {
        val downloadUrl = appEntity.downloadAppUrl ?: return null
        val md5Url = "$downloadUrl.md5"
        val (username, password) = getBasicAuth(appEntity)
        val httpRequest: HttpEntity<Void> = HttpEntity(CommonUtils.basicAuth(username, password))
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

    private fun getBasicAuth(app: AppEntity): BasicAuth {
        val username: String
        val password: String
        if (app.basicAuthUsername.isNullOrBlank() || app.basicAuthPassword.isNullOrBlank()) {
            if (app.certificationId.isNullOrBlank()) {
                val cert = certificationService.findById(app.certificationId!!)
                username = cert.username ?: throw AppException(400, "授权信息中的用户名为空")
                password = cert.password ?: throw AppException(400, "授权信息中的密码为空")
            } else {
                throw AppException(400, "必须先配置用户名/密码或配置授权信息")
            }
        } else {
            username = app.basicAuthUsername!!
            password = app.basicAuthPassword!!
        }
        return BasicAuth(username, password)
    }

}