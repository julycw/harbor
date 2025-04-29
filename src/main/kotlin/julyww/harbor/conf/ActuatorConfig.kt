package julyww.harbor.conf

import cn.trustway.nb.common.auth.security.AuthenticationInfo
import cn.trustway.nb.common.auth.security.PathAuthenticationTable
import cn.trustway.nb.common.auth.security.PathAuthenticationTableProvider
import cn.trustway.nb.common.auth.service.role.Permissions
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ActuatorConfig {

    @Value("\${spring.servlet.context-path:/harbor}")
    lateinit var contextPath: String

    @Value("\${management.endpoints.web.base-path:/actuator}")
    lateinit var actuatorBasePath: String

    @Value("\${spring.boot.admin.context-path:/spring-admin}")
    lateinit var springAdminBasePath: String

    @Value("\${spring.boot.admin.basic-auth.username}")
    lateinit var basicUsername: String

    @Value("\${spring.boot.admin.basic-auth.password}")
    lateinit var basicPassword: String
    @Bean
    fun actuatorFilter(): PathAuthenticationTableProvider {
        return PathAuthenticationTableProvider {
            listOf(
                PathAuthenticationTable(
                    listOf("$contextPath$actuatorBasePath/**"),
                    AuthenticationInfo.permission(setOf(Permissions.SUPREME_ALL))
                ),
                PathAuthenticationTable(
                    listOf("$contextPath${springAdminBasePath}/**"),
                    AuthenticationInfo.basic(basicUsername, basicPassword)
                )
            )
        }
    }
}