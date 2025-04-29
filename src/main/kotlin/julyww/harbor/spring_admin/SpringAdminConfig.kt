package julyww.harbor.spring_admin

import cn.trustway.nb.common.auth.autoconfig.remote.AuthServiceProvider
import com.google.common.cache.CacheBuilder
import de.codecentric.boot.admin.server.domain.entities.Instance
import de.codecentric.boot.admin.server.web.client.HttpHeadersProvider
import de.codecentric.boot.admin.server.web.client.InstanceExchangeFilterFunction
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import java.time.Duration


@Configuration
class SpringAdminConfig {

    private val cache = CacheBuilder.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(1))
        .build<String, String>()

    @Bean
    fun customHttpHeadersProvider(
        authServiceProvider: AuthServiceProvider
    ): HttpHeadersProvider {
        return HttpHeadersProvider { instance: Instance? ->
            val token = cache.get("token") {
                authServiceProvider.getJwtToken("system", Duration.ofMinutes(2))
            }
            val httpHeaders = HttpHeaders()
            httpHeaders.add("Authorization", "Bearer $token")
            httpHeaders
        }
    }


    @Bean
    fun instanceExchangeFilterFunction(): InstanceExchangeFilterFunction {
        return InstanceExchangeFilterFunction { instance, request, next ->
            next.exchange(request)
        }
    }
}