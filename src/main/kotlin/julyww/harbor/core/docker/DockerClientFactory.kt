package julyww.harbor.core.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import com.github.dockerjava.transport.DockerHttpClient
import org.springframework.stereotype.Component

@Component
class DockerClientFactory {
    fun create(
        maxConnections: Int = 24
    ): DockerClient {
        val config = DefaultDockerClientConfig.createDefaultConfigBuilder().build()
        val httpClient: DockerHttpClient = ApacheDockerHttpClient.Builder()
            .dockerHost(config.dockerHost)
            .sslConfig(config.sslConfig)
            .maxConnections(maxConnections)
            .build()
        return DockerClientBuilder.getInstance().withDockerHttpClient(httpClient).build()
    }
}