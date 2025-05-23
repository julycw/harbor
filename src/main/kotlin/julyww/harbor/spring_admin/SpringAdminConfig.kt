package julyww.harbor.spring_admin

import cn.trustway.nb.common.auth.autoconfig.remote.AuthServiceProvider
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import de.codecentric.boot.admin.server.domain.entities.Instance
import de.codecentric.boot.admin.server.web.client.HttpHeadersProvider
import de.codecentric.boot.admin.server.web.client.InstanceExchangeFilterFunction
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import java.time.Duration
import java.util.Base64
import java.util.concurrent.Executors


@ConditionalOnProperty(prefix = "spring.boot.admin.server", value = ["enabled"], havingValue = "true")
@Configuration
class SpringAdminConfig {

    private val logger = LoggerFactory.getLogger(SpringAdminConfig::class.java)

    @Bean
    fun customHttpHeadersProvider(
        authServiceProvider: AuthServiceProvider
    ): HttpHeadersProvider {
        val cache: LoadingCache<String, String> = CacheBuilder
            .newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .refreshAfterWrite(Duration.ofMinutes(5))
            .build(
                CacheLoader.asyncReloading(
                    object : CacheLoader<String, String>() {
                        override fun load(key: String): String {
                            try {
                                return authServiceProvider.getJwtToken("system", Duration.ofMinutes(10))
                            } catch (e: Exception) {
                                logger.warn("failed to load auth token: ${e.message}", e)
                                throw e
                            }
                        }
                    },
                    Executors.newFixedThreadPool(1)
                )
            )

        return HttpHeadersProvider { instance: Instance ->
            val httpBasic = instance.registration.metadata["basic"]
            val token = if (httpBasic == "true" || httpBasic == "1") {
                val username = instance.registration.metadata["username"]
                val password = instance.registration.metadata["password"]
                "Basic ${Base64.getEncoder().encodeToString("$username:$password".toByteArray())}"
            } else {
                "Bearer ${cache.get("token")}"
            }
            val httpHeaders = HttpHeaders()
            httpHeaders.add("Authorization", token)
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