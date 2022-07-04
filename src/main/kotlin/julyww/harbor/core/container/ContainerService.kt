package julyww.harbor.core.container

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.Statistics
import org.springframework.stereotype.Service
import java.io.Closeable
import java.time.Duration
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit


data class Container(
    val id: String,
    val name: String,
    val image: String,
    val status: String,
    val state: String
)

class StatsResultReceiver: ResultCallback<Statistics> {

    private val countDownLatch = CountDownLatch(1)
    private var stats: MutableList<Statistics> = mutableListOf()

    override fun onNext(data: Statistics) {
        stats.add(data)
    }

    override fun close() {
    }

    override fun onStart(closeable: Closeable?) {
    }

    override fun onError(throwable: Throwable?) {
    }

    override fun onComplete() {
        countDownLatch.countDown()
    }

    fun getStats(waitMilliSeconds: Long): List<Statistics> {
        if (countDownLatch.await(waitMilliSeconds, TimeUnit.MILLISECONDS)) {
            return stats
        }
        return emptyList()
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
            .exec(StatsResultReceiver()).getStats(10000)
    }

}