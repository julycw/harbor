package julyww.harbor.conf

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.IOException
import java.util.*
import javax.servlet.Filter
import javax.servlet.FilterChain
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse


class ActuatorFilter(
    val basicUser: String,
    val basicPassword: String
) : Filter {
    override fun doFilter(servletRequest: ServletRequest, servletResponse: ServletResponse, filterChain: FilterChain) {
        val request = servletRequest as HttpServletRequest
        val response = servletResponse as HttpServletResponse
        val auth = request.getHeader("Authorization")
        if (!auth.isNullOrBlank()) {
            val userAndPass: String = this.decodeBase64(auth.substring(6))
            val upArr = userAndPass.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (upArr.size != 2) {
                this.writeForbiddenCode(response)
            } else {
                val iptUser = upArr[0]
                val iptPass = upArr[1]
                if (iptUser == this.basicUser && iptPass == this.basicPassword) {
                    return filterChain.doFilter(request, response)
                } else {
                    this.writeForbiddenCode(response)
                }
            }
        } else {
            this.writeForbiddenCode(response)
        }
    }

    @Throws(IOException::class)
    private fun writeForbiddenCode(httpServletResponse: HttpServletResponse) {
        httpServletResponse.status = 401
        httpServletResponse.setHeader("WWW-Authenticate", "Basic realm=\"input Actuator Basic userName & password \"")
        httpServletResponse.writer.write("You do not have permission to access this resource")
    }

    private fun decodeBase64(source: String): String {
        return String(Base64.getDecoder().decode(source))
    }
}

@Configuration
class ActuatorConfig {

    @Value("\${management.endpoints.web.base-path:/actuator}")
    lateinit var actuatorBasePath: String

    @Bean
    fun actuatorFilter(): FilterRegistrationBean<ActuatorFilter> {
        val registrationBean = FilterRegistrationBean<ActuatorFilter>().apply {
            this.filter = ActuatorFilter(
                basicUser = "cdtnb",
                basicPassword = "cdtnb..."
            )
            this.order = Integer.MAX_VALUE
            this.setName("actuator-filter")
            this.addUrlPatterns("$actuatorBasePath/*")
        }
        return registrationBean
    }
}