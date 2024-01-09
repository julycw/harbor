package julyww.harbor.utils

import cn.trustway.nb.util.DateUtil
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class CommonUtilsTest {

    @Test
    fun testDockerDateParse() {
        val pattern = DateUtil.CHN_DATETIME_FORMAT
//        val pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSSX"
        val timeStr = "2024-01-09T07:51:44.942074727Z"
        val startAt = DateUtil.convert2Date(timeStr.substring(0, pattern.length).replace("T", " "), pattern)
        Assertions.assertEquals("2024-01-09 07:51:44", DateUtil.convert2String(startAt, pattern))
    }
}