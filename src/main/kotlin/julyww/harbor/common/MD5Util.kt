package julyww.harbor.common

import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.readBytes


class MD5Util {
    companion object {
        fun md5(bytes: ByteArray): String {
            val md5Digest = MessageDigest.getInstance("MD5")
            md5Digest.update(bytes)
            val digest = md5Digest.digest()
            val sb: StringBuilder = StringBuilder()
            for (byte in digest) {
                if (byte in 0..15) {
                    sb.append("0")
                }
                sb.append(Integer.toHexString(byte.toInt() and 0xff))
            }
            return sb.toString()
        }

        fun md5(path: Path): String {
            val bytes = path.readBytes()
            return md5(bytes)
        }
    }
}