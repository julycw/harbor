package julyww.harbor.core.monitor

import com.netflix.appinfo.InstanceInfo.InstanceStatus
import com.netflix.discovery.EurekaClient
import org.springframework.stereotype.Service
import java.util.*

open class Server(
    val host: String,
    val ip: String
)

class Application(
    val name: String,
    val instances: List<ApplicationInstance>
)

class ApplicationInstance(
    host: String,
    ip: String,
    val port: Int,
    val lastUpdated: Date,
    val status: InstanceStatus
) : Server(
    host = host,
    ip = ip
) {

}

@Service
class MonitorService(
    private val eurekaClient: EurekaClient
) {

    /**
     * 列出所有服务器
     */
    fun listServer(): List<Server> {
        return eurekaClient.applications.registeredApplications.map {
            it.instances
        }.flatten().distinctBy { it.ipAddr }
            .map {
                Server(
                    host = it.hostName,
                    ip = it.ipAddr
                )
            }
    }

    /**
     * 列出所有应用
     */
    fun listApplications(): List<Application> {
        return eurekaClient.applications.registeredApplications.map { app ->
            convert(app)
        }
    }

    /**
     * 根据应用名称获取应用
     */
    fun getApplication(name: String): Application? {
        return eurekaClient.applications.getRegisteredApplications(name)?.let { app ->
            convert(app)
        }
    }

    private fun convert(app: com.netflix.discovery.shared.Application): Application {
        return Application(
            name = app.name,
            instances = app.instances.map {
                ApplicationInstance(
                    host = it.hostName,
                    ip = it.ipAddr,
                    port = it.port,
                    lastUpdated = Date(it.lastUpdatedTimestamp),
                    status = it.status
                )
            }
        )
    }

}