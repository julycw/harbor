package julyww.harbor.core.application

import cn.trustway.nb.util.DateUtil
import cn.trustway.nb.util.DateUtil.CHN_DATETIME_FORMAT
import com.google.common.eventbus.Subscribe
import julyww.harbor.core.container.DockerService
import julyww.harbor.persist.IdGenerator
import julyww.harbor.persist.app.AppRepository
import julyww.harbor.persist.app.UpdateHistoryEntity
import julyww.harbor.persist.app.UpdateHistoryRepository
import julyww.harbor.persist.app.UpdateState
import julyww.harbor.props.HarborProps
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.data.repository.findByIdOrNull
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.io.path.name

@Service
class UpdateHistoryService(
    private val appRepository: AppRepository,
    private val updateHistoryRepository: UpdateHistoryRepository,
    private val dockerService: DockerService,
    private val idGenerator: IdGenerator,
    private val appEventBus: AppEventBus,
    private val harborProps: HarborProps
) : InitializingBean {

    private val log = LoggerFactory.getLogger(this::class.java)


    @Subscribe
    fun handleAppBeforeUpdate(event: AppBeforeUpdateEvent) {
        val app = appRepository.findByIdOrNull(event.appId) ?: return
        val history = updateHistoryRepository.findByApplicationId(event.appId)
        if (history.isEmpty()) {
            log.info("app(${app.name}) has no update history, auto make backup...")
            val origin = updateHistoryRepository.save(UpdateHistoryEntity(
                id = idGenerator.next(),
                applicationId = app.id!!,
                updateTime = Date(),
                updateFileMd5 = app.md5!!,
                state = UpdateState.Success
            ))
            doBackup(origin)?.let {
                origin.backupFilePath = it
                updateHistoryRepository.save(origin)
                log.info("app(${app.name}) auto make backup success!")
            }
        }
    }

    @Subscribe
    fun handleAppUpdated(event: AppUpdatedEvent) {
        val app = appRepository.findByIdOrNull(event.appId) ?: return
        val history = updateHistoryRepository.findByApplicationId(event.appId)

        var record = history.find { it.updateFileMd5 == app.md5 }?.apply {
            updateTime = Date()
            updateFileMd5 = app.md5!!
            state = UpdateState.Checking
        } ?: UpdateHistoryEntity(
            id = idGenerator.next(),
            applicationId = app.id!!,
            updateTime = Date(),
            updateFileMd5 = app.md5!!,
            state = UpdateState.Checking
        )
        record = updateHistoryRepository.save(record)
        doBackup(record)?.let {
            record.backupFilePath = it
            updateHistoryRepository.save(record)
        }
    }


    @Subscribe
    fun handleAppDeleted(event: AppDeletedEvent) {
        val history = updateHistoryRepository.findByApplicationId(event.appId)
        for (record in history) {
            removeBackup(record)
        }
        updateHistoryRepository.deleteAll(history)
    }

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    fun checkUpdateState() {
        val now = Date()
        val addMinutes = DateUtil.addMinutes(now, -15)
        val addMinutes2 = DateUtil.addMinutes(now, -30)
        val updateHistory = updateHistoryRepository.findAll().filter { it.state == UpdateState.Checking }
        for (record in updateHistory) {
            var newState: UpdateState? = null
            val app = appRepository.findByIdOrNull(record.applicationId)
            if (app == null) {
                newState = UpdateState.Fail
            } else {
                val inspect = dockerService.inspect(app.containerId!!)
                val startAt = DateUtil.convert2Date(inspect.state.startedAt, CHN_DATETIME_FORMAT)
                if (inspect.state.running == true && startAt.before(addMinutes)) {
                    newState = UpdateState.Success
                } else if (record.updateTime.before(addMinutes2)) {
                    newState = UpdateState.Fail
                }
            }

            if (newState != null) {
                updateHistoryRepository.save(record)
                if (newState == UpdateState.Success) {
                    var keep = 2
                    for (updateHistoryEntity in updateHistoryRepository.findByApplicationId(record.applicationId).sortedByDescending { it.updateTime }) {
                        if (updateHistoryEntity.state == UpdateState.Fail) {
                            updateHistoryRepository.delete(updateHistoryEntity)
                            removeBackup(updateHistoryEntity)
                        } else if (updateHistoryEntity.state == UpdateState.Success) {
                            if (keep > 0) {
                                updateHistoryRepository.delete(updateHistoryEntity)
                                removeBackup(updateHistoryEntity)
                                keep--
                            }
                        }
                    }
                }
            }
        }
    }

    private fun doBackup(historyRecord: UpdateHistoryEntity): String? {
        val app = appRepository.findByIdOrNull(historyRecord.applicationId) ?: return null
        val appPath = app.localAppPath?.let { Path.of(it) } ?: return null

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
            log.warn(e.message)
            return null
        }
    }

    private fun removeBackup(record: UpdateHistoryEntity) {
        if (!record.backupFilePath.isNullOrBlank()) {
            Files.deleteIfExists(Path.of(record.backupFilePath!!))
        }
    }

    override fun afterPropertiesSet() {
        appEventBus.register(this)
    }
}