package julyww.harbor.spring_admin

import cn.trustway.nb.common.auth.autoconfig.remote.AuthServiceProvider
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import de.codecentric.boot.admin.server.domain.entities.Instance
import de.codecentric.boot.admin.server.web.client.HttpHeadersProvider
import de.codecentric.boot.admin.server.web.client.InstanceExchangeFilterFunction
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import java.time.Duration
import java.util.concurrent.Executors


@Configuration
class SpringAdminConfig {

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
                            return authServiceProvider.getJwtToken("system", Duration.ofMinutes(10))
                        }
                    },
                    Executors.newFixedThreadPool(1)
                )
            )

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