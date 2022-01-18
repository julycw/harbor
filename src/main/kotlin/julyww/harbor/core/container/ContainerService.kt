package julyww.harbor.core.container

import com.github.dockerjava.api.DockerClient
import org.springframework.stereotype.Service

data class Container(
    val id: String,
    val name: String,
    val image: String,
    val status: String,
    val state: String
)

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

}