package julyww.harbor.rest

import cn.trustway.nb.common.auth.annotation.auth.RequiresAuthentication
import cn.trustway.nb.common.auth.annotation.auth.RequiresPermissions
import cn.trustway.nb.common.auth.annotation.ledger.WriteLedger
import julyww.harbor.common.PageResult
import julyww.harbor.core.*
import julyww.harbor.remote.SystemModuleList
import julyww.harbor.remote.SystemModuleManage
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.*

@RestControllerAdvice
class GlobalExceptionHandler {

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(
        value = [
            IllegalStateException::class,
            IllegalArgumentException::class
        ]
    )
    fun handleError(e: Exception): String? {
        return e.message
    }
}

@RequiresAuthentication
@RestController
class IndexController(
    private val containerService: ContainerService,
    private val appManageService: AppManageService
) {

    @RequiresPermissions(SystemModuleList)
    @GetMapping("container")
    fun listContainer(): List<Container> {
        return containerService.list()
    }

    @RequiresPermissions(SystemModuleList)
    @GetMapping("app")
    fun listApp(): PageResult<AppDTO> {
        return appManageService.list()
    }

    @WriteLedger(description = "新增/修改应用模块信息", targetId = "$#root", targetType = AppEntity::class)
    @RequiresPermissions(SystemModuleManage)
    @PostMapping("app")
    fun saveApp(@RequestBody app: AppEntity): Long? {
        return appManageService.save(app)
    }

    @WriteLedger(description = "删除应用模块信息", targetId = "#id", targetType = AppEntity::class)
    @RequiresPermissions(SystemModuleManage)
    @DeleteMapping("app/{id}")
    fun deleteApp(@PathVariable id: Long) {
        return appManageService.delete(id)
    }

    @WriteLedger(description = "查询应用日志信息", targetId = "#id", targetType = AppEntity::class)
    @RequiresPermissions(SystemModuleManage)
    @GetMapping("app/{id}/logs")
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

    @WriteLedger(description = "启动应用模块", targetId = "#id", targetType = AppEntity::class)
    @RequiresPermissions(SystemModuleManage)
    @PostMapping("app/{id}/start")
    fun startApp(@PathVariable id: Long) {
        appManageService.start(id)
    }

    @WriteLedger(description = "停止应用模块", targetId = "#id", targetType = AppEntity::class)
    @RequiresPermissions(SystemModuleManage)
    @PostMapping("app/{id}/stop")
    fun stopApp(@PathVariable id: Long) {
        appManageService.stop(id)
    }

    @WriteLedger(description = "更新应用模块", targetId = "#id", targetType = AppEntity::class)
    @RequiresPermissions(SystemModuleManage)
    @PostMapping("app/{id}/update")
    fun updateApp(@PathVariable id: Long) {
        appManageService.update(id)
    }

}