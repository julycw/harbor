package julyww.harbor.rest

import cn.trustway.nb.common.auth.annotation.auth.RequiresAuthentication
import cn.trustway.nb.common.auth.annotation.auth.RequiresPermissions
import cn.trustway.nb.common.auth.annotation.ledger.WriteLedger
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import julyww.harbor.common.PageResult
import julyww.harbor.core.application.*
import julyww.harbor.persist.app.AppEntity
import julyww.harbor.props.HarborProps
import julyww.harbor.remote.SystemModuleList
import julyww.harbor.remote.SystemModuleManage
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.*

@Api(tags = ["应用容器"])
@RequestMapping(value = ["/app-container", "/app"])
@RequiresAuthentication
@RestController
class AppController(
    private val appManageService: AppManageService,
    private val updateHistoryService: UpdateHistoryService,
    private val appInitService: AppInitService,
    private val harborProps: HarborProps
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

    @ApiOperation("永久保留更新记录的备份")
    @RequiresPermissions(SystemModuleManage)
    @PutMapping("{id}/update-history/{updateHistoryId}/keep-on")
    fun setAppUpdateHistoryKeep(@PathVariable id: Long, @PathVariable updateHistoryId: Long) {
        updateHistoryService.setKeep(updateHistoryId, true)
    }

    @ApiOperation("取消永久保留更新记录的备份")
    @RequiresPermissions(SystemModuleManage)
    @PutMapping("{id}/update-history/{updateHistoryId}/keep-off")
    fun setAppUpdateHistoryKeepOff(@PathVariable id: Long, @PathVariable updateHistoryId: Long) {
        updateHistoryService.setKeep(updateHistoryId, false)
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

    @ApiOperation("基于应用列表自动注册应用")
    @PostMapping("auto-register-apps")
    fun autoRegisterApps(
        @RequestParam(required = false) deploymentBaseDir: String?,
        @RequestParam(required = false) updateFileDownloadUrlPrefix: String?,
        @RequestParam(required = false, defaultValue = "false") override: Boolean,
    ) {
        appInitService.autoRegisterApps(
            deploymentBaseDir = deploymentBaseDir ?: harborProps.deploymentBaseDir,
            updateFileDownloadUrlPrefix = updateFileDownloadUrlPrefix ?: harborProps.updateFileDownloadUrlPrefix,
            override = override
        )
    }

}