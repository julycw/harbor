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
import julyww.harbor.utils.LockUtils
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.exchange
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
    private val idGenerator: IdGenerator
) {

    private val log = LoggerFactory.getLogger(AppManageService::class.java)

    private val downloadRestTemplate = let {
        val factory = SimpleClientHttpRequestFactory()
        factory.setConnectTimeout(1000)
        factory.setReadTimeout(2 * 60 * 1000)
        RestTemplate(factory)
    }

    private val restTemplate = let {
        val factory = SimpleClientHttpRequestFactory()
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
                    md5 = it.md5,
                    downloadAppUrl = it.downloadAppUrl,
                    localAppPath = it.localAppPath,
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
        LockUtils.lock(id) {
            executors.submit {
                appRepository.findByIdOrNull(id)?.let {
                    val remoteMd5 = remoteMd5(it)
                    if (autoSkip && remoteMd5 != null && remoteMd5 == it.md5) {
                        return@let
                    }

                    log.info("Updating ${it.name}...")

                    try {
                        val downloadUrl = it.downloadAppUrl ?: error("未设定下载地址")
                        val localPath = it.localAppPath ?: error("未设定部署地址")
                        val httpRequest: HttpEntity<Void> =
                            HttpEntity(CommonUtils.basicAuth(it.basicAuthUsername ?: "", it.basicAuthPassword ?: ""))
                        val response: ResponseEntity<ByteArray> = downloadRestTemplate.exchange(
                            downloadUrl,
                            HttpMethod.GET,
                            httpRequest,
                            ByteArray::class
                        )
                        it.md5 = MD5Util.md5(response.body!!)
                        if (it.checkMd5 == true) {
                            if (remoteMd5 != it.md5) {
                                error("MD5校验失败，请重试")
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
                        log.info("Updating ${it.name} failed: {}", e.message)
                        throw e
                    }
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