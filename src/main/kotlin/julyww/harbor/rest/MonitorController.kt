package julyww.harbor.rest

import julyww.harbor.core.monitor.Application
import julyww.harbor.core.monitor.MonitorService
import julyww.harbor.core.monitor.Server
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RequestMapping("monitor")
@RestController
class MonitorController(
    private val monitorService: MonitorService
) {

    @GetMapping("server")
    fun listServer(): List<Server> {
        return monitorService.listServer()
    }

    @GetMapping("application")
    fun listApplications(): List<Application> {
        return monitorService.listApplications()
    }

    @GetMapping("application/{name}")
    fun getApplication(@PathVariable name: String): Application {
        return monitorService.getApplication(name) ?: error("应用不存在")
    }

}