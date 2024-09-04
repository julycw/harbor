package julyww.harbor.core.certification.encrypt

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class JasyptUtilsTest {
    @Test
    fun testJasyptUtils() {
        val password = "harbor"
        val v = "hello"
        val encrypt = JasyptUtils.encrypt(v, password)
        val decrypt = JasyptUtils.decrypt(encrypt, password)
        Assertions.assertEquals(v, decrypt)

        val decrypt2 = JasyptUtils.decrypt("ENC($encrypt)", password)
        Assertions.assertEquals(v, decrypt2)
    }
}