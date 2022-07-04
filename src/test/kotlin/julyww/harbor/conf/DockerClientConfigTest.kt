package julyww.harbor.conf

import julyww.harbor.core.docker.DockerClientFactory
import org.junit.jupiter.api.Test

internal class DockerClientConfigTest {

    private val dockerClientFactory: DockerClientFactory = DockerClientFactory()

    @Test
    fun testDockerClient() {
        val client = dockerClientFactory.create()
        client.pingCmd().exec()
    }
}