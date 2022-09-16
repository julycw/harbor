package julyww.harbor.conf

import cn.trustway.nb.common.auth.security.AuthenticationInfo
import cn.trustway.nb.common.auth.security.PathAuthenticationTable
import cn.trustway.nb.common.auth.security.PathAuthenticationTableProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ActuatorConfig {

    @Value("\${management.endpoints.web.base-path:/actuator}")
    lateinit var actuatorBasePath: String

    @Bean
    fun actuatorFilter(): PathAuthenticationTableProvider {
        return PathAuthenticationTableProvider {
            listOf(
                PathAuthenticationTable(
                    listOf("/**${actuatorBasePath}/**"),
                    AuthenticationInfo(true, false, emptySet())
                )
            )
        }
    }
}