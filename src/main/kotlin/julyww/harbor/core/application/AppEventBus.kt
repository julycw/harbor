package julyww.harbor.core.application

import com.google.common.eventbus.EventBus
import org.springframework.stereotype.Component
import java.util.*
import java.util.concurrent.Executors


open class AppEvent(
    val appId: Long
)

class AppBeforeUpdateEvent(appId: Long) : AppEvent(appId)

class AppUpdatedEvent(
    val updateTime: Date,
    appId: Long
) : AppEvent(appId)

class AppDeletedEvent(appId: Long) : AppEvent(appId)


@Component
class AppEventBus {
    val appManageEventBus: EventBus = EventBus(this::class.java.simpleName)

    fun post(event: AppEvent) {
        appManageEventBus.post(event)
    }

    fun register(listener: Any) {
        appManageEventBus.register(listener)
    }
}