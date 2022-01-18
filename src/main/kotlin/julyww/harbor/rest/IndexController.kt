package julyww.harbor.rest

import cn.trustway.nb.common.auth.annotation.auth.RequiresAuthentication
import cn.trustway.nb.common.auth.annotation.auth.RequiresPermissions
import cn.trustway.nb.common.auth.annotation.ledger.WriteLedger
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import julyww.harbor.common.PageResult
import julyww.harbor.core.application.AppDTO
import julyww.harbor.core.application.AppManageService
import julyww.harbor.core.container.Container
import julyww.harbor.core.container.ContainerService
import julyww.harbor.core.host.HostService
import julyww.harbor.core.host.SshSessionManager
import julyww.harbor.persist.app.AppEntity
import julyww.harbor.persist.host.HostEntity
import julyww.harbor.remote.SystemHostList
import julyww.harbor.remote.SystemHostManage
import julyww.harbor.remote.SystemModuleList
import julyww.harbor.remote.SystemModuleManage
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
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
}

@Api(tags = ["应用"])
@RequestMapping("/app")
@RequiresAuthentication
@RestController
class AppController(
    private val appManageService: AppManageService
) {

    @ApiOperation("分页查询应用列表")
    @RequiresPermissions(SystemModuleList)
    @GetMapping
    fun listApp(): PageResult<AppDTO> {
        return appManageService.list()
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
    @WriteLedger(description = "查询应用日志信息", targetId = "#id", targetType = AppEntity::class)
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

data class ExecSshCommandRequest(
    val command: String
)

data class OutputBound(
    val content: String
)

@Api(tags = ["主机管理"])
@RequestMapping("/host")
@RestController
class HostController(
    private val sshSessionManager: SshSessionManager,
    private val hostService: HostService
) {

    @ApiOperation("分页查询服务器列表")
    @RequiresPermissions(SystemHostList)
    @GetMapping
    fun listHost(): PageResult<HostEntity> {
        return hostService.list()
    }

    @ApiOperation("新增/修改服务器信息")
    @WriteLedger(description = "新增/修改服务器模块信息", targetId = "$#root", targetType = HostEntity::class)
    @RequiresPermissions(SystemHostManage)
    @PostMapping
    fun saveHost(@RequestBody app: HostEntity): Long? {
        return hostService.save(app)
    }

    @ApiOperation("删除服务器信息")
    @WriteLedger(description = "删除主机模块信息", targetId = "#id", targetType = HostEntity::class)
    @RequiresPermissions(SystemHostManage)
    @DeleteMapping("{id}")
    fun deleteHost(@PathVariable id: Long) {
        return hostService.delete(id)
    }


    @WriteLedger(description = "远程连接服务器", targetId = "#id", targetType = HostEntity::class)
    @ApiOperation("连接服务器, 返回session id")
    @RequiresPermissions(SystemHostManage)
    @PostMapping("{id}/session")
    fun createSession(@PathVariable id: Long): String {
        return hostService.findById(id)?.let { host ->
            sshSessionManager.createSession(
                username = host.username ?: throw error("必须先配置登陆用户名"),
                password = host.password ?: throw error("必须先配置登陆密码"),
                host = host.ip ?: throw error("必须先配置IP地址"),
                port = host.port ?: 22
            ).sessionId
        } ?: throw error("未找到相关配置")
    }

    @ApiOperation("打开SSH会话", notes = "该接口为SSE接口，数据包内容为Stdout/Stderr内容")
    @GetMapping("ssh-session/{id}/ssh")
    fun createSshChannel(@PathVariable id: String): ResponseEntity<SseEmitter> {
        val emitter = SseEmitter(0L)
        sshSessionManager.getSession(id)?.let { session ->
            emitter.onCompletion {
                sshSessionManager.closeSession(session.sessionId)
            }
            session.openShellChannel { msg ->
                emitter.send(OutputBound(msg))
            }
        }
        return ResponseEntity.ok().header("X-Accel-Buffering", "no").body(emitter)
    }

    @ApiOperation("关闭 Session")
    @RequiresPermissions(SystemHostManage)
    @DeleteMapping("ssh-session/{id}")
    fun closeSshChannel(@PathVariable id: String) {
        sshSessionManager.closeSession(id)
    }

    @ApiOperation("向SSH会话发送命令")
    @RequiresPermissions(SystemHostManage)
    @PostMapping("ssh-session/{id}/command")
    fun exec(@PathVariable id: String, @RequestBody request: ExecSshCommandRequest) {
        val session = sshSessionManager.getSession(id) ?: throw error("session 已关闭")
        session.exec(request.command)
    }
}