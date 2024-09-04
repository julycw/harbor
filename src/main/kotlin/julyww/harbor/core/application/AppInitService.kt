package julyww.harbor.core.application

import cn.hutool.crypto.digest.MD5
import cn.trustway.nb.util.HashUtil
import julyww.harbor.core.container.DockerService
import julyww.harbor.persist.app.AppEntity
import julyww.harbor.props.HarborProps
import org.springframework.boot.CommandLineRunner
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import kotlin.io.path.Path
import kotlin.io.path.pathString

class AppTemplate(
    val name: String,
    val fileName: String,
    val matchContainerNames: Collection<String>,
    val restartCommand: String? = null,
    val checkMD5: Boolean = true,
    val restartAfterUpdate: Boolean = true,
    val rollbackIfUpdateFall: Boolean = true,
    val autoUpdateAt: String? = null,
    val autoRestartAt: String? = null
)

val appTemplates = listOf(
    AppTemplate(
        name = "用户授权模块",
        fileName = "auth-service.jar",
        matchContainerNames = setOf("auth-service"),
        autoUpdateAt = "04:00:00"
    ),
    AppTemplate(
        name = "勤务模块",
        fileName = "auth-duty-service.jar",
        matchContainerNames = setOf("duty-service", "auth-duty-service"),
        autoUpdateAt = "04:05:00"
    ),
    AppTemplate(
        name = "对象存储模块",
        fileName = "oss-service.jar",
        matchContainerNames = setOf("oss-service"),
        autoUpdateAt = "04:30:00"
    ),
    AppTemplate(
        name = "统计模块",
        fileName = "sz-statistic-service.jar",
        matchContainerNames = setOf("sz-statistic-service"),
        autoUpdateAt = "04:30:00"
    ),
    AppTemplate(
        name = "即时通讯模块",
        fileName = "auth-im-service.jar",
        matchContainerNames = setOf("auth-im-service"),
        autoUpdateAt = "04:35:00"
    ),
    AppTemplate(
        name = "流程查询模块",
        fileName = "auth-bpmn-query.jar",
        matchContainerNames = setOf("auth-bpmn-query", "bpmn-query"),
        autoUpdateAt = "04:10:00"
    ),
    AppTemplate(
        name = "流程核心模块",
        fileName = "auth-bpmn-service.jar",
        matchContainerNames = setOf("auth-bpmn-service", "bpmn-core", "bpmn-service"),
        autoUpdateAt = "04:15:00"
    ),
    AppTemplate(
        name = "业务查询模块",
        fileName = "query-service.jar",
        matchContainerNames = setOf("query-service"),
        autoUpdateAt = "04:45:00"
    ),
    AppTemplate(
        name = "远程执法模块",
        fileName = "sz-trff-service.jar",
        matchContainerNames = setOf("sz-trff-service"),
        autoUpdateAt = "03:35:00"
    ),
    AppTemplate(
        name = "远程执法模块",
        fileName = "sz-trff-service.jar",
        matchContainerNames = setOf("sz-trff-service"),
        autoUpdateAt = "04:45:00"
    ),
    AppTemplate(
        name = "事故模块",
        fileName = "sz-accident-service.jar",
        matchContainerNames = setOf("sz-accident-service"),
        autoUpdateAt = "04:50:00"
    ),
    AppTemplate(
        name = "交治模块",
        fileName = "sz-traffic-service.jar",
        matchContainerNames = setOf("sz-traffic-service"),
        autoUpdateAt = "04:50:00"
    ),
    AppTemplate(
        name = "视频录制管理模块",
        fileName = "video-service.jar",
        matchContainerNames = setOf("video-service"),
        autoUpdateAt = "04:00:00"
    ),
    AppTemplate(
        name = "短链服务",
        fileName = "short-url-service.jar",
        matchContainerNames = setOf("short-url-service"),
        autoUpdateAt = "04:00:00"
    ),
    AppTemplate(
        name = "支付服务",
        fileName = "payment-service.jar",
        matchContainerNames = setOf("payment-service"),
        autoUpdateAt = "04:00:00"
    ),
    AppTemplate(
        name = "模块管理器",
        fileName = "harbor.jar",
        matchContainerNames = setOf("harbor")
    ),
)

@Service
class AppInitService(
    private val harborProps: HarborProps,
    private val appManageService: AppManageService,
    private val dockerService: DockerService
) : CommandLineRunner {

    fun autoRegisterApps() {

        val alreadyExistApps = appManageService.list(
            AppQueryBean(
                filterByEndpointMatch = true,
                filterByContainerExist = true,
                withRemoteMd5 = false
            )
        )

        val containers = dockerService.list()
        for (container in containers) {
            val containerName = container.name.removePrefix("/")
            for (appTemplate in appTemplates) {
                if (appTemplate.matchContainerNames.any { containerName == it }) {
                    if (alreadyExistApps.list.any { it.containerId == container.id }) {
                        break
                    }

                    val localPath = Path(harborProps.deploymentBaseDir).resolve(appTemplate.fileName)
                    val md5 = try {
                        val file = localPath.toFile()
                        MD5.create().digestHex(file)
                    } catch (e: Exception) {
                        null
                    }

                    appManageService.save(
                        AppEntity(
                            id = null,
                            name = appTemplate.name,
                            containerId = container.id,
                            md5 = md5,
                            downloadAppUrl = harborProps.updateFileDownloadUrlPrefix + appTemplate.fileName,
                            localAppPath = localPath.pathString,
                            certificationId = "default",
                            basicAuthUsername = null,
                            basicAuthPassword = null,
                            version = null,
                            latestUpdateTime = null,
                            autoRestart = appTemplate.restartAfterUpdate,
                            checkMd5 = appTemplate.checkMD5,
                            scheduleRestart = !appTemplate.autoRestartAt.isNullOrBlank(),
                            restartAt = appTemplate.autoRestartAt,
                            scheduleUpdate = !appTemplate.autoUpdateAt.isNullOrBlank(),
                            updateAt = appTemplate.autoUpdateAt,
                            endpoint = null,
                            autoRollbackWhenUpdateFail = appTemplate.rollbackIfUpdateFall,
                            reloadCmd = appTemplate.restartCommand,
                        )
                    )

                    break
                }
            }
        }
    }

    @Scheduled(cron = "\${harbor.auto-register-apps-cron}")
    fun autoRegisterAppsSchedule() {
        autoRegisterApps()
    }

    override fun run(vararg args: String?) {
        if (harborProps.autoRegisterAppsOnStartUp) {
            autoRegisterApps()
        }
    }
}