package julyww.harbor.utils

import cn.trustway.nb.common.auth.exception.app.AppException
import java.util.*

val appUpdateState: MutableMap<String, Long> = Collections.synchronizedMap(mutableMapOf())
object LockUtils {

    fun check(target: Any) {
        val targetId = target.toString()
        if (appUpdateState[targetId] != null && appUpdateState[targetId] != Thread.currentThread().id) {
            throw AppException(400, "正在执行动作，请勿重复发起请求")
        }
    }

    fun lock(target: Any, block: () -> Unit) {
        val targetId = target.toString()
        check(target)
        appUpdateState[targetId] = Thread.currentThread().id
        try {
            check(target)
            block()
        } finally {
            appUpdateState.remove(targetId)
        }
    }

}