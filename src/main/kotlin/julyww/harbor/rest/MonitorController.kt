package julyww.harbor.rest

import cn.trustway.nb.common.auth.annotation.auth.RequiresAuthentication
import cn.trustway.nb.common.auth.exception.app.AppException
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import julyww.harbor.core.monitor.Application
import julyww.harbor.core.monitor.MonitorService
import julyww.harbor.core.monitor.Server
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RequiresAuthentication
@Api(tags = ["服务监控"])
@RequestMapping("monitor")
@RestController
class MonitorController(
    private val monitorService: MonitorService
) {

    @ApiOperation("获取服务器实例列表")
    @GetMapping("server")
    fun listServer(): List<Server> {
        return monitorService.listServer()
    }

    @ApiOperation("获取注册中心微服务列表")
    @GetMapping("application")
    fun listApplications(): List<Application> {
        return monitorService.listApplications()
    }

    @ApiOperation("获取指定微服务详情")
    @GetMapping("application/{name}")
    fun getApplication(@PathVariable name: String): Application {
        return monitorService.getApplication(name) ?: throw AppException(400, "应用不存在")
    }

}