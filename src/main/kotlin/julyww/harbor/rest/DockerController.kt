package julyww.harbor.rest

import cn.trustway.nb.common.auth.annotation.auth.RequiresAuthentication
import cn.trustway.nb.common.auth.annotation.auth.RequiresPermissions
import cn.trustway.nb.common.auth.exception.app.AppException
import com.github.dockerjava.api.command.InspectContainerResponse
import com.github.dockerjava.api.model.Info
import com.github.dockerjava.api.model.Statistics
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import julyww.harbor.core.container.Container
import julyww.harbor.core.container.DockerContainerSessionManager
import julyww.harbor.core.container.DockerService
import julyww.harbor.remote.SystemModuleList
import julyww.harbor.remote.SystemModuleManage
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter


@Api(tags = ["Docker"])
@RequiresAuthentication
@RequestMapping
@RestController
class DockerController(
    private val dockerService: DockerService,
    private val dockerContainerSessionManager: DockerContainerSessionManager
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


    @RequiresPermissions(SystemModuleManage)
    @ApiOperation("创建容器命令行通道")
    @GetMapping("container/{id}/attach")
    fun createChannel(@PathVariable id: String, @RequestParam(required = false, defaultValue = "sh") cmd: String = "sh"): String {
        return dockerContainerSessionManager.createSession(id, cmd).sessionId
    }

    @RequiresPermissions(SystemModuleManage)
    @ApiOperation("连接容器命令行通道", notes = "该接口为SSE接口，数据包内容为Stdout/Stderr内容")
    @GetMapping("container-session/{sessionId}/attach")
    fun attach(@PathVariable sessionId: String): ResponseEntity<SseEmitter> {
        val emitter = SseEmitter(0L)
        val session = dockerContainerSessionManager.getSession(sessionId)
        session.attach { msg ->
            emitter.send(OutputBound(msg))
        }
        emitter.onCompletion {
            session.close()
        }
        return ResponseEntity.ok().header("X-Accel-Buffering", "no").body(emitter)
    }

    @ApiOperation("关闭容器命令行通道")
    @RequiresPermissions(SystemModuleManage)
    @DeleteMapping("container-session/{sessionId}")
    fun closeSshChannel(@PathVariable sessionId: String) {
        dockerContainerSessionManager.close(sessionId)
    }

    @ApiOperation("容器命令行通道发送命令")
    @RequiresPermissions(SystemModuleManage)
    @PostMapping("container-session/{id}/command")
    fun exec(@PathVariable id: String, @RequestBody request: ExecSshCommandRequest) {
        val session = dockerContainerSessionManager.getSession(id)
        session.exec(request.command)
    }
}

