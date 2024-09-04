package julyww.harbor.core.certification.encrypt

import org.jasypt.encryption.StringEncryptor
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig

fun stringEncryptor(password: String): StringEncryptor {
    val encryptor = PooledPBEStringEncryptor()
    val config = SimpleStringPBEConfig()
    config.setPassword(password)
    config.algorithm = "PBEWITHHMACSHA512ANDAES_256"
    config.setKeyObtentionIterations("1000")
    config.setPoolSize("1")
    config.providerName = "SunJCE"
    config.setSaltGeneratorClassName("org.jasypt.salt.RandomSaltGenerator")
    config.setIvGeneratorClassName("org.jasypt.iv.RandomIvGenerator")
    config.stringOutputType = "base64"
    encryptor.setConfig(config)
    return encryptor
}

object JasyptUtils {
    fun encrypt(plaintext: String, password: String): String {
        return stringEncryptor(password).encrypt(plaintext)
    }

    fun decrypt(data: String, password: String): String {
        if (data.startsWith("ENC(") && data.endsWith(")")) {
            return stringEncryptor(password).decrypt(data.substring(4, data.length - 1))
        }
        return stringEncryptor(password).decrypt(data)
    }
}