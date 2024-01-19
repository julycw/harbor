package julyww.harbor.core.host

import cn.trustway.nb.common.auth.exception.app.AppException
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import org.springframework.stereotype.Service
import java.nio.file.*
import java.util.*
import kotlin.io.path.*
import kotlin.streams.toList

@ApiModel
data class File(
    @ApiModelProperty
    val fileName: String,

    @ApiModelProperty
    val isDir: Boolean,

    @ApiModelProperty
    val size: Long?,

    @ApiModelProperty
    val extension: String?,

    @ApiModelProperty
    val owner: String?,

    @ApiModelProperty
    val lastModifyTime: Date?
)

@ApiModel
data class FileContent(

    @ApiModelProperty
    val content: String?
)

@Service
class DiskFileService {

    fun ls(path: String): List<File> {
        return Files.list(Path.of(path)).map {
            File(
                it.name,
                it.isDirectory(),
                try {
                    it.fileSize()
                } catch (_: Exception) {
                    null
                },
                it.extension,
                it.getOwner()?.name,
                Date.from(it.getLastModifiedTime().toInstant())
            )
        }.toList()
    }

    fun mv(path: String, fileName: String, newPath: String, newFileName: String) {
        Files.move(
            Path.of(path).resolve(fileName),
            Path.of(newPath).resolve(newFileName)
        )
    }

    fun read(path: String, fileName: String): FileContent {
        val filePath = getFilePath(path, fileName)
        if (filePath.fileSize() > 1024 * 256) { // 256KB
            throw AppException(400, "暂不支持查看大于256KB的文件")
        }
        return FileContent(Files.readString(Path.of(path).resolve(fileName)))
    }

    fun save(path: String, fileName: String, content: String) {
        Files.writeString(
            Path.of(path).resolve(fileName),
            content,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING
        )
    }

    private fun getFilePath(path: String, fileName: String): Path {
        val filePath = Path.of(path).resolve(fileName)
        if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
            throw AppException(400, "$filePath not exist")
        }
        return filePath
    }

}