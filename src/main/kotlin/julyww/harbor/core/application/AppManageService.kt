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
import julyww.harbor.core.container.DockerContainerSessionManager
import julyww.harbor.core.container.DockerService
import julyww.harbor.persist.IdGenerator
import julyww.harbor.persist.app.AppEntity
import julyww.harbor.persist.app.AppRepository
import julyww.harbor.persist.app.UpdateHistoryRepository
import julyww.harbor.utils.CommonUtils
import julyww.harbor.utils.Environments
import julyww.harbor.utils.LockUtils
import org.apache.http.impl.client.HttpClients
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.exchange
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.concurrent.Executors

@ApiModel
data class AppDTO(
    @ApiModelProperty("ID")
    var id: Long?,
    @ApiModelProperty("应用名称")
    var name: String,
    @ApiModelProperty("容器ID", notes = "重新创建容器后会发生变化")
    var containerId: String?,
    @ApiModelProperty("应用程序文件MD5")
    var md5: String?,
    @ApiModelProperty("文件下载地址")
    var downloadAppUrl: String?,
    @ApiModelProperty("本地部署目录")
    var localAppPath: String?,
    @ApiModelProperty("授权信息")
    var certificationId: String?,
    @ApiModelProperty("Basic Auth 用户名", notes = "建议用certificationId替换")
    var basicAuthUsername: String?,
    @ApiModelProperty("Basic Auth 密码", notes = "建议用certificationId替换")
    var basicAuthPassword: String?,
    @ApiModelProperty("版本号")
    var version: String?,
    @ApiModelProperty("最近更新时间")
    var latestUpdateTime: Date?,
    @ApiModelProperty("远程下载地址中的MD5")
    var remoteMd5: String?,
    @ApiModelProperty("更新完后是否自动重启")
    var autoRestart: Boolean?,
    @ApiModelProperty("更新时是否检查MD5")
    var checkMd5: Boolean?,
    @ApiModelProperty("是否定时重启")
    var scheduleRestart: Boolean?,
    @ApiModelProperty("定时重启时间")
    var restartAt: String?,
    @ApiModelProperty("是否自动更新")
    var scheduleUpdate: Boolean?,
    @ApiModelProperty("更新检查时间")
    var updateAt: String?,
    @ApiModelProperty("部署点位")
    var endpoint: String?,
    @ApiModelProperty("更新失败后是否自动回滚")
    var autoRollbackWhenUpdateFail: Boolean?,
    @ApiModelProperty("重启命令")
    var reloadCmd: String?
)

data class BasicAuth(
    val username: String,
    val password: String
)

@ApiModel
class AppQueryBean(

    @ApiModelProperty("是否基于endpoint是否匹配来进行过滤")
    var filterByEndpointMatch: Boolean = true,

    @ApiModelProperty("是否基于container是否存在来进行过滤")
    var filterByContainerExist: Boolean = false,

    @ApiModelProperty("是否获取远端md5")
    var withRemoteMd5: Boolean = true
)

@Service
class AppManageService(
    private val appRepository: AppRepository,
    private val dockerClient: DockerClient,
    private val dockerService: DockerService,
    private val idGenerator: IdGenerator,
    private val certificationService: CertificationService,
    private val updateHistoryRepository: UpdateHistoryRepository,
    private val appEventBus: AppEventBus,
    private val environments: Environments,
    private val dockerContainerSessionManager: DockerContainerSessionManager
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

    fun find(id: Long): AppDTO {
        val it = appRepository.findByIdOrNull(id) ?: throw AppException(400, "应用不存在")
        return AppDTO(
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
            remoteMd5 = remoteMd5(it),
            checkMd5 = it.checkMd5,
            scheduleRestart = it.scheduleRestart,
            restartAt = it.restartAt,
            scheduleUpdate = it.scheduleUpdate,
            updateAt = it.updateAt,
            endpoint = it.endpoint,
            autoRollbackWhenUpdateFail = it.autoRollbackWhenUpdateFail,
            reloadCmd = it.reloadCmd
        )
    }

    fun list(query: AppQueryBean): PageResult<AppDTO> {
        var list = appRepository.findAll().toList()
        if (query.filterByEndpointMatch) {
            list = list.filter { it.endpoint.isNullOrBlank() || it.endpoint == environments.endpoint }
        }
        if (query.filterByContainerExist) {
            val containers = try {
                dockerService.list().map { it.id }.toSet()
            } catch (e: Exception) {
                emptyList()
            }
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
                    endpoint = it.endpoint,
                    autoRollbackWhenUpdateFail = it.autoRollbackWhenUpdateFail,
                    reloadCmd = it.reloadCmd
                )
            },
            total = list.size.toLong()
        )
    }

    fun save(entity: AppEntity): Long? {
        if (entity.id == null) {
            entity.id = idGenerator.next()
        }
        if (!entity.containerId.isNullOrBlank()) {
            if (dockerService.inspect(entity.containerId!!) != null) {
                entity.endpoint = environments.endpoint
            }
        }
        return appRepository.save(entity).id
    }

    fun delete(id: Long) {
        appRepository.deleteById(id)
        appEventBus.post(AppDeletedEvent(id))
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
            start(it)
        }
    }

    fun start(app: AppEntity) {
        app.containerId?.let { containerId ->
            if (containerId.isNotBlank()) {
                dockerClient.startContainerCmd(containerId).exec()
            }
        }
    }

    fun stop(id: Long) {
        appRepository.findByIdOrNull(id)?.let {
            stop(it)
        }
    }

    fun stop(it: AppEntity) {
        it.containerId?.let { containerId ->
            if (containerId.isNotBlank()) {
                dockerClient.stopContainerCmd(containerId).exec()
            }
        }
    }

    fun restart(id: Long) {
        appRepository.findByIdOrNull(id)?.let {
            restart(it)
        }
    }


    fun restart(it: AppEntity) {
        val containerId = it.containerId
        if (containerId.isNullOrBlank()) {
            throw AppException(400, "未配置对应容器，无法重启")
        }
        log.info("Restarting ${it.name}...")
        try {
            val reloadCmd = it.reloadCmd?.trim()
            if (reloadCmd.isNullOrBlank()) {
                dockerClient.restartContainerCmd(containerId).exec()
            } else {
                val session = dockerContainerSessionManager.createSession(containerId, "sh")
                try {
                    var output = ""
                    session.attach {
                        output += it
                    }

                    log.info("Restart by command: '$reloadCmd' ...")
                    session.exec(reloadCmd + "\n")
                    Thread.sleep(5000)
                    log.info("Restart Output: $output")
                } catch (e: Exception) {
                    throw e
                } finally {
                    session.close()
                }
            }
            log.info("Restart ${it.name} finish!")
        } catch (e: Exception) {
            log.info("Restart ${it.name} failed: {}", e.message)
            throw e
        }
    }

    fun update(id: Long, autoSkip: Boolean = false) {
        executors.submit {
            LockUtils.lock(id) {
                appRepository.findByIdOrNull(id)?.let { app ->
                    val now = Date()
                    val downloadUrl = app.downloadAppUrl ?: throw AppException(400, "未设定下载地址")
                    val localPath = app.localAppPath ?: throw AppException(400, "未设定部署地址")
                    val remoteMd5 = remoteMd5(app)
                    if (autoSkip && remoteMd5 != null && remoteMd5 == app.md5) {
                        return@let
                    }
                    log.info("Updating ${app.name}...")
                    appEventBus.post(AppBeforeUpdateEvent(id))

                    try {
                        val basicAuth = getBasicAuth(app)
                        val httpRequest: HttpEntity<Void> = if (basicAuth != null) {
                            val (username, password) = basicAuth
                            HttpEntity(CommonUtils.basicAuth(username, password))
                        } else {
                            HttpEntity(HttpHeaders())
                        }

                        log.info("Updating ${app.name}, begin download...")
                        val response: ResponseEntity<ByteArray> = downloadRestTemplate.exchange(
                            downloadUrl,
                            HttpMethod.GET,
                            httpRequest,
                            ByteArray::class
                        )
                        log.info("Updating ${app.name}, download success")
                        val downloadedFileMd5 = SecureUtil.md5().digestHex(response.body)
                        if (app.checkMd5 == true) {
                            if (remoteMd5 != downloadedFileMd5) {
                                throw AppException(400, "MD5校验失败，请重试")
                            }
                        }
                        app.md5 = downloadedFileMd5

                        doUpdate(app, downloadUrl, response.body!!, localPath)
                        log.info("Updating ${app.name} finish")

                        appEventBus.post(AppUpdatedEvent(now, id))

                    } catch (e: Exception) {
                        log.error("Updating ${app.name} failed: {}", e.message)
                    }
                }
            }
        }
    }

    fun updateByUploadFile(id: Long, file: MultipartFile) {
        LockUtils.lock(id) {
            appRepository.findByIdOrNull(id)?.let { app ->
                val now = Date()
                val localPath = app.localAppPath ?: throw AppException(400, "未设定部署地址")
                log.info("Updating ${app.name}...")
                appEventBus.post(AppBeforeUpdateEvent(id))
                try {
                    app.md5 = SecureUtil.md5().digestHex(file.bytes)
                    doUpdate(app, file.originalFilename!!, file.bytes, localPath)
                    log.info("Updating ${app.name} finish")
                    appEventBus.post(AppUpdatedEvent(now, id))
                } catch (e: Exception) {
                    log.error("Updating ${app.name} failed: {}", e.message)
                    throw AppException(500, e.message)
                }
            }
        }
    }

    fun rollback(id: Long, updateHistoryId: Long) {
        executors.submit {
            LockUtils.lock(id) {
                appRepository.findByIdOrNull(id)?.let { app ->
                    val localPath = app.localAppPath ?: throw AppException(400, "未设定部署地址")
                    val updateHistory = updateHistoryRepository.findById(updateHistoryId).map { UpdateHistoryDTO(it) }
                        .orElseThrow { AppException(400, "更新历史不存在") }
                    if (updateHistory.applicationId != app.id) {
                        throw AppException(400, "更新历史不存在")
                    }
                    if (!updateHistory.rollbackAble) {
                        throw AppException(400, "无法回滚")
                    }
                    log.info("Rollback ${app.name} to ${updateHistory.updateTime}...")

                    try {

                        if (app.autoRestart && app.reloadCmd.isNullOrBlank() && app.containerId != environments.dockerContainerId) {
                            try {
                                stop(id)
                            } catch (ignore: Exception) {
                            }
                        }

                        val backupFIle = Path.of(updateHistory.backupFilePath!!).toFile()

                        if (backupFIle.name.endsWith(".tar")) {
                            CommonUtils.tarUnArchive(backupFIle.readBytes(), localPath)
                        } else if (backupFIle.name.endsWith(".zip")) {
                            CommonUtils.zipUnArchive(backupFIle.readBytes(), localPath)
                        } else {
                            Files.copy(
                                backupFIle.toPath(),
                                Path.of(localPath),
                                StandardCopyOption.REPLACE_EXISTING,
                                StandardCopyOption.COPY_ATTRIBUTES
                            )
                        }
                        app.md5 = updateHistory.updateFileMd5
                        app.latestUpdateTime = Date()
                        appRepository.save(app)
                        log.info("Rollback ${app.name} finish")

                        // 回滚时强制重启
                        restart(id)
                    } catch (e: Exception) {
                        log.error("Rollback ${app.name} failed: {}", e.message)
                        throw e
                    }
                }
            }
        }
    }

    fun localMd5(appEntity: AppEntity): String? {
        return try {
            appEntity.localAppPath?.let {
                val md5 = SecureUtil.md5().digestHex(Path.of(it).toFile())
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
        return try {
            val basicAuth = getBasicAuth(appEntity)
            val httpRequest: HttpEntity<Void> = if (basicAuth != null) {
                val (username, password) = basicAuth
                HttpEntity(CommonUtils.basicAuth(username, password))
            } else {
                HttpEntity(HttpHeaders())
            }
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

    private fun doUpdate(app: AppEntity, updateFileName: String, fileContent: ByteArray, targetLocalPath: String) {
        if (app.autoRestart && app.reloadCmd.isNullOrBlank() && app.containerId != environments.dockerContainerId) {
            try {
                stop(app)
            } catch (ignore: Exception) {
            }
        }

        if (updateFileName.endsWith(".tar")) {
            CommonUtils.tarUnArchive(fileContent, targetLocalPath)
        } else if (updateFileName.endsWith(".zip")) {
            CommonUtils.zipUnArchive(fileContent, targetLocalPath)
        } else {
            Files.write(
                Path.of(targetLocalPath),
                fileContent,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            )
        }
        app.endpoint = environments.endpoint
        app.latestUpdateTime = Date()
        appRepository.save(app)

        if (app.autoRestart) {
            restart(app)
        }
    }

    private fun getBasicAuth(app: AppEntity): BasicAuth? {
        val username: String
        val password: String
        if (app.basicAuthUsername.isNullOrBlank() || app.basicAuthPassword.isNullOrBlank()) {
            val certificationId = if (app.certificationId.isNullOrBlank()) "default" else app.certificationId!!
            val cert = certificationService.findById(certificationId) ?: return null
            username = cert.username ?: throw AppException(400, "授权信息中的用户名为空")
            password = cert.password ?: throw AppException(400, "授权信息中的密码为空")
        } else {
            username = app.basicAuthUsername!!
            password = app.basicAuthPassword!!
        }
        return BasicAuth(username, password)
    }

}