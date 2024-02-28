package julyww.harbor.core.application

import cn.hutool.crypto.SecureUtil
import cn.trustway.nb.common.auth.exception.app.AppException
import cn.trustway.nb.util.DateUtil
import cn.trustway.nb.util.DateUtil.CHN_DATETIME_FORMAT
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.eventbus.Subscribe
import io.swagger.annotations.ApiModelProperty
import julyww.harbor.core.container.DockerService
import julyww.harbor.persist.IdGenerator
import julyww.harbor.persist.app.*
import julyww.harbor.props.HarborProps
import julyww.harbor.utils.Environments
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.data.repository.findByIdOrNull
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.io.path.name

val fileMd5Cache: Cache<String, String> = CacheBuilder.newBuilder().expireAfterWrite(Duration.ofHours(24)).build()

class UpdateHistoryDTO(
    var id: Long,
    var applicationId: Long,
    var updateTime: Date,
    var updateFileMd5: String,
    var state: UpdateState,
    var keep: Boolean?,
    var backupFilePath: String? = null
) {

    @get:ApiModelProperty("备份文件是否存在")
    val backUpFileExist: Boolean by lazy {
        try {
            if (backupFilePath.isNullOrBlank()) {
                false
            } else if (!Files.exists(Path.of(backupFilePath!!))) {
                false
            } else true
        } catch (_: Exception) {
            false
        }
    }

    @get:ApiModelProperty("是否可进行回滚")
    val rollbackAble: Boolean by lazy {
        if (!backUpFileExist) {
            false
        } else {
            try {
                updateFileMd5 == backupFilePath?.let {
                    fileMd5Cache.get(it) {
                        SecureUtil.md5().digestHex(Path.of(it).toFile())
                    }
                }
            } catch (_: Exception) {
                false
            }
        }
    }

    constructor(entity: UpdateHistoryEntity) : this(
        id = entity.id!!,
        applicationId = entity.applicationId,
        updateTime = entity.updateTime,
        updateFileMd5 = entity.updateFileMd5,
        state = entity.state,
        keep = entity.keep,
        backupFilePath = entity.backupFilePath,
    )

}

@Service
class UpdateHistoryService(
    private val appManageService: AppManageService,
    private val appRepository: AppRepository,
    private val updateHistoryRepository: UpdateHistoryRepository,
    private val dockerService: DockerService,
    private val idGenerator: IdGenerator,
    private val appEventBus: AppEventBus,
    private val harborProps: HarborProps,
    private val environments: Environments
) : InitializingBean {

    private val log = LoggerFactory.getLogger(this::class.java)

    fun findById(id: Long): UpdateHistoryDTO {
        return updateHistoryRepository.findById(id).map { UpdateHistoryDTO(it) }.orElseThrow()
    }

    fun setKeep(id: Long, keep: Boolean) {
        updateHistoryRepository.findById(id).ifPresent {
            if (it.state != UpdateState.Success) {
                throw AppException(400, "只有更新成功的记录才可以永久保留")
            }
            it.keep = keep
            updateHistoryRepository.save(it)
        }
    }

    fun listByApp(appId: Long): List<UpdateHistoryDTO> {
        return updateHistoryRepository.findByApplicationId(appId).sortedByDescending { it.updateTime }
            .map { UpdateHistoryDTO(it) }
    }


    @Subscribe
    fun handleAppBeforeUpdate(event: AppBeforeUpdateEvent) {
        val app = appRepository.findByIdOrNull(event.appId) ?: return
        val history = updateHistoryRepository.findByApplicationId(event.appId)
        if (history.isEmpty()) {
            log.info("app ${app.name} has no update history, auto make backup...")
            val backupFilePath = doBackup(app.id!!)
            if (!backupFilePath.isNullOrBlank()) {
                val record = UpdateHistoryEntity(
                    id = idGenerator.next(),
                    applicationId = app.id!!,
                    updateTime = app.latestUpdateTime ?: Date(),
                    updateFileMd5 = app.md5!!,
                    backupFilePath = doBackup(app.id!!),
                    state = UpdateState.Success
                )
                updateHistoryRepository.save(record)
                log.info("app ${app.name} auto make backup success!")
            }
        }
    }

    @Subscribe
    fun handleAppUpdated(event: AppUpdatedEvent) {
        val app = appRepository.findByIdOrNull(event.appId) ?: return
        val history =
            updateHistoryRepository.findByApplicationId(event.appId).filter { it.state == UpdateState.Checking }
        for (record in history) {
            try {
                removeBackupFile(record)
            } catch (_: Exception) {
            }
        }
        updateHistoryRepository.deleteAll(history)

        val record = UpdateHistoryEntity(
            id = idGenerator.next(),
            applicationId = app.id!!,
            updateTime = event.updateTime,
            updateFileMd5 = app.md5!!,
            state = UpdateState.Checking,
            backupFilePath = doBackup(app.id!!)
        )
        updateHistoryRepository.save(record)
    }


    @Subscribe
    fun handleAppDeleted(event: AppDeletedEvent) {
        val history = updateHistoryRepository.findByApplicationId(event.appId)
        for (record in history) {
            try {
                removeBackupFile(record)
            } catch (_: Exception) {
            }
        }
        updateHistoryRepository.deleteAll(history)
    }

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    fun checkUpdateState() {
        val now = Date()
        // 超过这个分钟数后容器状态始终保持为running，则标记为成功
        val minutes = 5
        // 超过这个分钟数后容器状态还没变为running，标记为更新失败
        val minutes2 = 10
        val addMinutes = DateUtil.addMinutes(now, -minutes)
        val addMinutes2 = DateUtil.addMinutes(now, -minutes2)
        val updateHistory = updateHistoryRepository.findAll().filter { it.state == UpdateState.Checking }
        for (record in updateHistory) {
            var newState: UpdateState? = null
            val app = appRepository.findByIdOrNull(record.applicationId)
            if (app == null) {
                newState = UpdateState.Fail
            } else if (app.endpoint != environments.endpoint) {
                continue
            } else {
                val inspect = dockerService.inspect(app.containerId!!)
                if (inspect == null) {
                    newState = UpdateState.Fail
                } else {
                    val startAt = inspect.state.startedAt?.let { parseDockerDate(it) }
                    log.info(
                        "checking app ${app.name}... running: ${inspect.state.running}, startAt: ${dateFormat(startAt)}, updateAt: ${
                            dateFormat(
                                record.updateTime
                            )
                        }"
                    )
                    if (
                        inspect.state.running == true &&
                        startAt?.before(addMinutes) == true &&
                        record.updateTime.before(addMinutes)
                    ) {
                        newState = UpdateState.Success
                        log.info("app ${app.name} is running last $minutes minutes, mark as Success!")
                    } else if (record.updateTime.before(addMinutes2)) {
                        newState = UpdateState.Fail
                        log.warn("app ${app.name} is not running $minutes2 minutes after updated, mark as Fail!")
                        // 自动回滚
                        if (app.autoRollbackWhenUpdateFail != false) {
                            log.warn("app ${app.name} is try to rollback...")
                            rollbackToLatestSuccessVersion(app)
                        }
                    }
                }
            }

            if (newState != null) {
                record.state = newState
                updateHistoryRepository.save(record)
                if (newState == UpdateState.Success) {
                    var keep = 3
                    for (updateHistoryEntity in updateHistoryRepository.findByApplicationId(record.applicationId)
                        .sortedByDescending { it.updateTime }) {
                        if (updateHistoryEntity.keep == true) {
                            continue
                        }
                        if (updateHistoryEntity.state == UpdateState.Fail) {
                            updateHistoryRepository.delete(updateHistoryEntity)
                            removeBackupFile(updateHistoryEntity)
                        } else if (updateHistoryEntity.state == UpdateState.Success) {
                            if (keep > 0) {
                                keep--
                                continue
                            }
                            updateHistoryRepository.delete(updateHistoryEntity)
                            removeBackupFile(updateHistoryEntity)
                        }
                    }
                }
            }
        }
    }

    private fun rollbackToLatestSuccessVersion(app: AppEntity) {
        val oldVersion = listByApp(app.id!!)
            .filter { it.state == UpdateState.Success && it.rollbackAble }
            .maxByOrNull { it.updateTime }
        if (oldVersion != null) {
            appManageService.rollback(app.id!!, oldVersion.id)
            log.warn("app ${app.name} rollback done!")
        } else {
            log.warn("app ${app.name} rollback failed!")
        }
    }

    private fun parseDockerDate(time: String): Date {
        return DateUtil.addHours(
            DateUtil.convert2Date(
                time.substring(0, CHN_DATETIME_FORMAT.length).replace("T", " "),
                CHN_DATETIME_FORMAT
            ), 8
        )
    }

    private fun doBackup(appId: Long): String? {
        val app = appRepository.findByIdOrNull(appId) ?: return null
        val appPath = app.localAppPath?.let { Path.of(it) }
        if (appPath == null) {
            log.warn("backup for ${app.name} failed! appPath is empty")
            return null
        }

        if (!Files.exists(appPath)) {
            log.warn("backup for ${app.name} failed! local app path '${app.localAppPath}' not exist!")
            return null
        }

        try {
            val backupDir = Path.of(harborProps.backupDir, app.name)
            if (Files.notExists(backupDir)) {
                Files.createDirectory(backupDir)
            }
            val backupFile = backupDir.resolve(appPath.name + "." + System.currentTimeMillis())

            Files.copy(
                appPath,
                backupFile,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.COPY_ATTRIBUTES
            )

            return backupFile.toString()
        } catch (e: Exception) {
            log.warn("backup for ${app.name} failed! {}", e.message)
            return null
        }
    }

    private fun removeBackupFile(record: UpdateHistoryEntity) {
        if (!record.backupFilePath.isNullOrBlank()) {
            Files.deleteIfExists(Path.of(record.backupFilePath!!))
        }
    }

    override fun afterPropertiesSet() {
        appEventBus.register(this)
    }

    private fun dateFormat(date: Date?) = date?.let { DateUtil.convert2String(it, CHN_DATETIME_FORMAT) }
}