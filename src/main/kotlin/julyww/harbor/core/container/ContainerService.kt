package julyww.harbor.core.container

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.Statistics
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit


data class Container(
    val id: String,
    val name: String,
    val image: String,
    val status: String,
    val state: String
)

class StatsResultReceiver : ResultCallback.Adapter<Statistics>() {

    private var stats: MutableList<Statistics> = mutableListOf()

    override fun onNext(data: Statistics) {
        stats.add(data)
    }

    fun getStats(waitMilliSeconds: Long): MutableList<Statistics> {
        awaitCompletion(waitMilliSeconds, TimeUnit.MILLISECONDS)
        return stats
    }
}

@Service
class ContainerService(
    private val dockerClient: DockerClient
) {

    fun list(): List<Container> {
        return dockerClient.listContainersCmd().withShowAll(true).exec().map {
            Container(
                id = it.id,
                name = it.names.joinToString(separator = ", "),
                image = it.image,
                state = it.state,
                status = it.status
            )
        }
    }

    fun stats(containerId: String): List<Statistics> {
        return dockerClient.statsCmd(containerId)
            .withNoStream(true)
            .exec(StatsResultReceiver())
            .getStats(10000)
    }

}