package julyww.harbor.rest

import cn.trustway.nb.common.auth.annotation.auth.RequiresPermissions
import cn.trustway.nb.common.auth.annotation.ledger.WriteLedger
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import julyww.harbor.common.PageResult
import julyww.harbor.core.host.HostService
import julyww.harbor.core.host.SshSessionManager
import julyww.harbor.persist.host.HostEntity
import julyww.harbor.remote.SystemHostList
import julyww.harbor.remote.SystemHostManage
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

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


    @ApiOperation("根据IP查询服务器")
    @RequiresPermissions(SystemHostList)
    @GetMapping("query")
    fun findHostByIP(@RequestParam ip: String): HostEntity {
        return hostService.findByIP(ip) ?: error("指定IP的服务器不存在")
    }

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
            try {
                sshSessionManager.createSession(
                    username = host.username ?: error("必须先配置登陆用户名"),
                    password = host.password ?: error("必须先配置登陆密码"),
                    host = host.ip ?: error("必须先配置IP地址"),
                    port = host.port ?: 22
                ).sessionId
            } catch (e: Exception) {
                error(e.message ?: e.javaClass.name)
            }
        } ?: error("未找到相关配置")
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
        val session = sshSessionManager.getSession(id) ?: error("session 已关闭")
        session.exec(request.command)
    }
}