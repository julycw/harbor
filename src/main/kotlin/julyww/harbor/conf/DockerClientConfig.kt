package julyww.harbor.conf

import com.github.dockerjava.api.DockerClient
import julyww.harbor.core.docker.DockerClientFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class DockerClientConfig {

    @Bean
    fun dockerClient(factory: DockerClientFactory): DockerClient {
        return factory.create()
    }

}