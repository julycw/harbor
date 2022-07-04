package julyww.harbor.rest

import cn.trustway.nb.common.auth.annotation.auth.RequiresAuthentication
import cn.trustway.nb.common.auth.annotation.auth.RequiresPermissions
import cn.trustway.nb.common.auth.annotation.ledger.WriteLedger
import com.github.dockerjava.api.model.Statistics
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import julyww.harbor.common.PageResult
import julyww.harbor.core.application.AppDTO
import julyww.harbor.core.application.AppManageService
import julyww.harbor.core.application.AppQueryBean
import julyww.harbor.core.container.Container
import julyww.harbor.core.container.ContainerService
import julyww.harbor.persist.app.AppEntity
import julyww.harbor.remote.SystemModuleList
import julyww.harbor.remote.SystemModuleManage
import org.springframework.web.bind.annotation.*
import java.util.*


@Api(tags = ["容器"])
@RequiresAuthentication
@RequestMapping("/container")
@RestController
class ContainerController(
    private val containerService: ContainerService
) {

    @ApiOperation("查询容器列表")
    @RequiresPermissions(SystemModuleList)
    @GetMapping
    fun listContainer(): List<Container> {
        return containerService.list()
    }

    @ApiOperation("查询容器状态")
    @RequiresPermissions(SystemModuleList)
    @GetMapping("{id}/stats")
    fun statsContainer(@PathVariable id: String): List<Statistics> {
        return containerService.stats(id)
    }
}

@Api(tags = ["应用容器"])
@RequestMapping(value = ["/app-container", "/app"])
@RequiresAuthentication
@RestController
class AppController(
    private val appManageService: AppManageService
) {

    @ApiOperation("分页查询应用列表")
    @RequiresPermissions(SystemModuleList)
    @GetMapping
    fun listApp(query: AppQueryBean): PageResult<AppDTO> {
        return appManageService.list(query)
    }

    @ApiOperation("新增/修改应用模块信息")
    @WriteLedger(description = "新增/修改应用模块信息", targetId = "$#root", targetType = AppEntity::class)
    @RequiresPermissions(SystemModuleManage)
    @PostMapping
    fun saveApp(@RequestBody app: AppEntity): Long? {
        return appManageService.save(app)
    }

    @ApiOperation("删除应用模块信息")
    @WriteLedger(description = "删除应用模块信息", targetId = "#id", targetType = AppEntity::class)
    @RequiresPermissions(SystemModuleManage)
    @DeleteMapping("{id}")
    fun deleteApp(@PathVariable id: Long) {
        return appManageService.delete(id)
    }

    @ApiOperation("查询应用日志信息")
    @RequiresPermissions(SystemModuleManage)
    @GetMapping("{id}/logs")
    fun getAppLogs(
        @PathVariable id: Long,
        @RequestParam(required = false) since: Long?,
        @RequestParam(required = false, defaultValue = "500") tail: Int,
        @RequestParam(required = false, defaultValue = "false") withTimestamps: Boolean
    ): List<String> {
        return appManageService.log(
            id,
            tail = tail,
            since = since?.let { Date(it) },
            withTimestamps = withTimestamps
        )
    }

    @ApiOperation("启动应用模块")
    @WriteLedger(description = "启动应用模块", targetId = "#id", targetType = AppEntity::class)
    @RequiresPermissions(SystemModuleManage)
    @PostMapping("{id}/start")
    fun startApp(@PathVariable id: Long) {
        appManageService.start(id)
    }

    @ApiOperation("停止应用模块")
    @WriteLedger(description = "停止应用模块", targetId = "#id", targetType = AppEntity::class)
    @RequiresPermissions(SystemModuleManage)
    @PostMapping("{id}/stop")
    fun stopApp(@PathVariable id: Long) {
        appManageService.stop(id)
    }

    @ApiOperation("重启应用模块")
    @WriteLedger(description = "重启应用模块", targetId = "#id", targetType = AppEntity::class)
    @RequiresPermissions(SystemModuleManage)
    @PostMapping("{id}/restart")
    fun restartApp(@PathVariable id: Long) {
        appManageService.restart(id)
    }

    @ApiOperation("更新应用模块")
    @WriteLedger(description = "更新应用模块", targetId = "#id", targetType = AppEntity::class)
    @RequiresPermissions(SystemModuleManage)
    @PostMapping("{id}/update")
    fun updateApp(@PathVariable id: Long) {
        appManageService.update(id)
    }

}
