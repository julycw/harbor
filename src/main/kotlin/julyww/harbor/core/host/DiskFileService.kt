package julyww.harbor.core.host

import cn.trustway.nb.common.auth.exception.app.AppException
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import lujing.common.data.desensitization.Base64EncryptDesensitization
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
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


@ApiModel
data class FileExist(

    @ApiModelProperty
    val exist: Boolean
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

    @ExperimentalPathApi
    fun remove(path: String, fileName: String) {
        Path.of(path).resolve(fileName).deleteRecursively()
    }

    fun mkdir(path: String, fileName: String) {
        Files.createDirectory(Path.of(path).resolve(fileName))
    }

    fun readFile(path: String, fileName: String): java.io.File {
        return getFilePath(path, fileName).toFile()
    }

    fun read(path: String, fileName: String, encrypt: Boolean): FileContent {
        val filePath = getFilePath(path, fileName)
        if (filePath.fileSize() > 1024 * 256) { // 256KB
            throw AppException(400, "暂不支持查看大于256KB的文件")
        }
        val content = Files.readString(Path.of(path).resolve(fileName))
        return FileContent(if (encrypt) Base64EncryptDesensitization().desensitize(content) else content)
    }

    fun save(path: String, fileName: String, content: String, contentIsEncrypt: Boolean) {
        Files.writeString(
            Path.of(path).resolve(fileName),
            if (contentIsEncrypt) Base64EncryptDesensitization.restore(content) else content,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING
        )
    }

    fun exist(path: String, fileName: String?): FileExist {
        var filePath = Path.of(path)
        if (!fileName.isNullOrBlank()) {
            filePath = filePath.resolve(fileName)
        }
        return FileExist(Files.exists(filePath))
    }

    private fun getFilePath(path: String, fileName: String): Path {
        val filePath = Path.of(path).resolve(fileName)
        if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
            throw AppException(400, "$filePath not exist")
        }
        return filePath
    }

}