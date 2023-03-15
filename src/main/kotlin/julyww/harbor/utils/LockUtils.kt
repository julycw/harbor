package julyww.harbor.utils

import java.util.*

val appUpdateState: MutableMap<String, Long> = Collections.synchronizedMap(mutableMapOf())
object LockUtils {

    fun check(target: Any) {
        val targetId = target.toString()
        if (appUpdateState[targetId] != null && appUpdateState[targetId] != Thread.currentThread().id) {
            error("正在更新，请勿重复操作")
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