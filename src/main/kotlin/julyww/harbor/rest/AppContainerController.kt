package julyww.harbor.rest

import cn.trustway.nb.common.auth.annotation.auth.RequiresAuthentication
import cn.trustway.nb.common.auth.annotation.auth.RequiresPermissions
import cn.trustway.nb.common.auth.annotation.ledger.WriteLedger
import cn.trustway.nb.common.auth.exception.app.AppException
import com.github.dockerjava.api.command.InspectContainerResponse
import com.github.dockerjava.api.model.Info
import com.github.dockerjava.api.model.Statistics
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import julyww.harbor.common.PageResult
import julyww.harbor.core.application.*
import julyww.harbor.core.container.Container
import julyww.harbor.core.container.DockerService
import julyww.harbor.persist.app.AppEntity
import julyww.harbor.persist.app.UpdateHistoryEntity
import julyww.harbor.remote.SystemModuleList
import julyww.harbor.remote.SystemModuleManage
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.*


@Api(tags = ["Docker"])
@RequiresAuthentication
@RequestMapping
@RestController
class DockerController(
    private val dockerService: DockerService
) {

    @ApiOperation("查询Docker信息")
    @RequiresPermissions(SystemModuleList)
    @GetMapping("docker-info")
    fun dockerInfo(): Info {
        return dockerService.sys()
    }

    @ApiOperation("查询容器列表")
    @RequiresPermissions(SystemModuleList)
    @GetMapping("container")
    fun listContainer(): List<Container> {
        return dockerService.list()
    }

    @ApiOperation("查询容器状态")
    @RequiresPermissions(SystemModuleList)
    @GetMapping("container/{id}/stats")
    fun statsContainer(@PathVariable id: String): List<Statistics> {
        return dockerService.stats(id)
    }

    @ApiOperation("查询容器信息")
    @RequiresPermissions(SystemModuleList)
    @GetMapping("container/{id}/inspect")
    fun inspectContainer(@PathVariable id: String): InspectContainerResponse {
        return dockerService.inspect(id) ?: throw AppException(400, "container not exist")
    }
}

@Api(tags = ["应用容器"])
@RequestMapping(value = ["/app-container", "/app"])
@RequiresAuthentication
@RestController
class AppController(
    private val appManageService: AppManageService,
    private val updateHistoryService: UpdateHistoryService
) {

    @ApiOperation("查询应用详情")
    @RequiresPermissions(SystemModuleList)
    @GetMapping("{id}")
    fun findApp(@PathVariable id: Long): AppDTO {
        return appManageService.find(id)
    }

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

    @ApiOperation("查询更新记录")
    @RequiresPermissions(SystemModuleManage)
    @GetMapping("{id}/update-history")
    fun getAppUpdateHistory(@PathVariable id: Long): List<UpdateHistoryDTO> {
        return updateHistoryService.listByApp(id)
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


    @ApiOperation("回滚应用模块")
    @WriteLedger(description = "滚回应用模块", targetId = "#id", targetType = AppEntity::class)
    @RequiresPermissions(SystemModuleManage)
    @PostMapping("{id}/rollback/{updateHistoryId}")
    fun rollbackApp(@PathVariable id: Long, @PathVariable updateHistoryId: Long) {
        appManageService.rollback(id, updateHistoryId)
    }

    @ApiOperation("更新应用模块(直接上传)")
    @WriteLedger(description = "更新应用模块", targetId = "#id", targetType = AppEntity::class)
    @RequiresPermissions(SystemModuleManage)
    @PostMapping("{id}/update-upload")
    fun updateAppByUploadFile(@PathVariable id: Long, file: MultipartFile) {
        appManageService.updateByUploadFile(id, file)
    }

}
