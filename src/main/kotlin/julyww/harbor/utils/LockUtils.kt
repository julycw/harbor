package julyww.harbor.utils

import java.util.*

val appUpdateState: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
object LockUtils {
    fun lock(target: Any, block: () -> Unit) {
        val targetId = target.toString()
        if (appUpdateState.contains(targetId)) {
            error("正在更新，请勿重复操作")
        }

        if (appUpdateState.contains(targetId)) {
            error("正在更新，请勿重复操作")
        }
        appUpdateState.add(targetId)
        try {
            block()
        } finally {
            appUpdateState.remove(targetId)
        }
    }

}