package julyww.harbor.utils

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.springframework.http.HttpHeaders
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.*

class CommonUtils {
    companion object {

        /**
         * 构造一个包含basic auth的http request header
         */
        fun basicAuth(username: String, password: String): HttpHeaders {
            return object : HttpHeaders() {
                init {
                    val auth = "$username:$password"
                    val encodedAuth: ByteArray = Base64.getEncoder().encode(auth.toByteArray())
                    val authHeader = "Basic " + String(encodedAuth)
                    set("Authorization", authHeader)
                }
            }
        }

        /**
         * tar 文件解压
         */
        fun tarUnArchive(file: ByteArray, outputDir: String) {
            ByteArrayInputStream(file).use { fileInputStream ->
                val tarArchiveInputStream = TarArchiveInputStream(fileInputStream)
                var entry: TarArchiveEntry? = null
                while (tarArchiveInputStream.nextTarEntry?.also { entry = it } != null) {
                    if (entry!!.isDirectory) {
                        continue
                    }
                    val outputFile = File(Paths.get(outputDir, entry!!.name).toUri())
                    if (!outputFile.parentFile.exists()) {
                        outputFile.parentFile.mkdirs()
                    }
                    Files.write(
                        outputFile.toPath(),
                        tarArchiveInputStream.readAllBytes(),
                        StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING
                    )
                }
            }
        }

        /**
         * zip文件解压
         */
        fun zipUnArchive(file: ByteArray, outputDir: String) {
            ByteArrayInputStream(file).use { fileInputStream ->
                val archiveInputStream = ZipArchiveInputStream(fileInputStream)
                var entry: ZipArchiveEntry? = null
                while (archiveInputStream.nextZipEntry?.also { entry = it } != null) {
                    if (entry!!.isDirectory) {
                        continue
                    }
                    val outputFile = File(Paths.get(outputDir, entry!!.name).toUri())
                    if (!outputFile.parentFile.exists()) {
                        outputFile.parentFile.mkdirs()
                    }
                    Files.write(
                        outputFile.toPath(),
                        archiveInputStream.readAllBytes(),
                        StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING
                    )
                }
            }
        }
    }
}