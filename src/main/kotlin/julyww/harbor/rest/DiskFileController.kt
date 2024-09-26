package julyww.harbor.rest

import cn.trustway.nb.common.auth.annotation.auth.RequiresAuthentication
import cn.trustway.nb.common.auth.annotation.auth.RequiresPermissions
import io.swagger.annotations.Api
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import io.swagger.annotations.ApiOperation
import julyww.harbor.core.host.DiskFileService
import julyww.harbor.core.host.File
import julyww.harbor.core.host.FileContent
import julyww.harbor.core.host.FileExist
import julyww.harbor.remote.SystemHostManage
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import kotlin.io.path.ExperimentalPathApi


@ApiModel
data class SaveFileRequest(
    @ApiModelProperty
    val path: String,
    @ApiModelProperty
    val fileName: String,
    @ApiModelProperty
    val content: String,
    @ApiModelProperty
    val encrypt: Boolean?
)

@ApiModel
data class MoveFileRequest(
    @ApiModelProperty
    val path: String,
    @ApiModelProperty
    val fileName: String,
    @ApiModelProperty
    val newPath: String,
    @ApiModelProperty
    val newFileName: String
)


@ApiModel
data class MakeDirRequest(
    @ApiModelProperty
    val path: String,
    @ApiModelProperty
    val fileName: String,
)


@ApiModel
data class RemoveRequest(
    @ApiModelProperty
    val path: String,
    @ApiModelProperty
    val fileName: String,
)


@Api(tags = ["文件管理"])
@RequiresAuthentication
@RequestMapping("disk-file")
@RestController
class DiskFileController(
    private val diskFileService: DiskFileService
) {

    @RequiresPermissions(SystemHostManage)
    @ApiOperation("列出文件列表")
    @GetMapping("ls")
    fun ls(@RequestParam path: String): List<File> {
        return diskFileService.ls(path)
    }

    @RequiresPermissions(SystemHostManage)
    @ApiOperation("重命名/移动文件")
    @PostMapping("mv")
    fun mv(
        @RequestBody request: MoveFileRequest
    ) {
        return diskFileService.mv(request.path, request.fileName, request.newPath, request.newFileName)
    }

    @ExperimentalPathApi
    @RequiresPermissions(SystemHostManage)
    @ApiOperation("删除文件（递归）")
    @PostMapping("remove")
    fun remove(
        @RequestBody request: RemoveRequest
    ) {
        return diskFileService.remove(request.path, request.fileName)
    }

    @RequiresPermissions(SystemHostManage)
    @ApiOperation("创建目录")
    @PostMapping("mkdir")
    fun mkdir(
        @RequestBody request: MakeDirRequest
    ) {
        return diskFileService.mkdir(request.path, request.fileName)
    }

    @RequiresPermissions(SystemHostManage)
    @ApiOperation("读取文件内容")
    @GetMapping("read-content")
    fun read(
        @RequestParam path: String,
        @RequestParam fileName: String
    ): FileContent {
        return diskFileService.read(path, fileName)
    }

    @RequiresPermissions(SystemHostManage)
    @ApiOperation("下载文件内容")
    @GetMapping("download")
    fun download(
        @RequestParam path: String,
        @RequestParam fileName: String
    ): ResponseEntity<Resource> {
        val file = diskFileService.readFile(path, fileName)
        val resource = FileSystemResource(file)
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .contentLength(file.length())
            .header("Content-Disposition", ("attachment; filename=\"" + file.getName()) + "\"")
            .body(resource)
    }

    @RequiresPermissions(SystemHostManage)
    @ApiOperation("判定文件是否存在")
    @GetMapping("exist")
    fun exist(
        @RequestParam path: String,
        @RequestParam(required = false) fileName: String?
    ): FileExist {
        return diskFileService.exist(path, fileName)
    }

    @RequiresPermissions(SystemHostManage)
    @ApiOperation("保存文件内容")
    @PostMapping("save-content")
    fun save(@RequestBody request: SaveFileRequest) {
        return diskFileService.save(request.path, request.fileName, request.content, request.encrypt ?: false)
    }

}