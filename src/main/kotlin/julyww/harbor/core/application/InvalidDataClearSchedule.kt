package julyww.harbor.core.application

import julyww.harbor.core.container.DockerService
import julyww.harbor.persist.app.AppRepository
import julyww.harbor.persist.app.UpdateHistoryRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

//@Component
class InvalidDataClearSchedule(
    private val appManageService: AppManageService,
    private val dockerService: DockerService
) {


//    @Scheduled(cron = "0 0 0 * * ?")
    fun clear() {
        val appList = appManageService.list(
            AppQueryBean(
                filterByEndpointMatch = true,
                filterByContainerExist = false
            )
        ).list.filter { !it.endpoint.isNullOrBlank() && !it.containerId.isNullOrBlank() }

        if (appList.isEmpty()) return

        val containerList = try {
            dockerService.list()
        } catch (e: Exception) {
            return
        }

        val containers = containerList.groupBy { it.id }.mapValues { it.value.first() }

        for (app in appList) {
            val container = containers[app.containerId]
            if (container == null) {
                clearApp(app.id!!)
            }
        }

    }

    private fun clearApp(appId: Long) {
        appManageService.delete(appId)
    }

}
