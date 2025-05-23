package julyww.harbor.spring_admin

import cn.trustway.nb.common.auth.autoconfig.remote.ConfigServiceHelper
import cn.trustway.nb.util.SSLUtil
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import de.codecentric.boot.admin.server.domain.entities.Instance
import de.codecentric.boot.admin.server.domain.entities.InstanceRepository
import de.codecentric.boot.admin.server.domain.events.InstanceEvent
import de.codecentric.boot.admin.server.domain.events.InstanceInfoChangedEvent
import de.codecentric.boot.admin.server.domain.events.InstanceStatusChangedEvent
import de.codecentric.boot.admin.server.notify.AbstractEventNotifier
import julyww.harbor.core.certification.CertificationService
import julyww.harbor.props.HarborProps
import julyww.harbor.utils.CommonUtils
import org.apache.http.impl.client.HttpClients
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.exchange
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.Optional
import java.util.concurrent.Executors

@ConditionalOnBean(InstanceRepository::class)
@ConditionalOnProperty(prefix = "spring.boot.admin.server", value = ["enabled"], havingValue = "true")
@Component
class CustomNotifier(
    repository: InstanceRepository,
    private val harborProps: HarborProps,
    private val certificationService: CertificationService,
    private val configServiceHelper: ConfigServiceHelper
) : AbstractEventNotifier(repository) {

    private val logger = LoggerFactory.getLogger(CustomNotifier::class.java)

    private val restTemplate = let {
        val factory = HttpComponentsClientHttpRequestFactory()
        val httpClient = HttpClients.custom()
            .setSSLContext(SSLUtil.sslContextNoCheck())
            .setSSLHostnameVerifier(SSLUtil::hostnameVerifier)
            .build()
        factory.httpClient = httpClient
        factory.setConnectTimeout(1000)
        factory.setReadTimeout(5000)
        RestTemplate(factory)
    }

    val sysNameCache: LoadingCache<String, Optional<String>> = CacheBuilder
        .newBuilder()
        .expireAfterWrite(Duration.ofMinutes(1440))
        .refreshAfterWrite(Duration.ofMinutes(1))
        .build(
            CacheLoader.asyncReloading(
                object : CacheLoader<String, Optional<String>>() {
                    override fun load(key: String): Optional<String> {
                        try {
                            return Optional.ofNullable(
                                configServiceHelper.readGlobalConfig("system-deploy-name").stringValue()
                            )
                        } catch (e: Exception) {
                            throw e
                        }
                    }
                },
                Executors.newFixedThreadPool(1)
            )
        )

    override fun doNotify(
        event: InstanceEvent,
        instance: Instance
    ): Mono<Void> {
        return Mono.fromRunnable {
            val certification = certificationService.findById(harborProps.healthReportCertification)
            val values = mutableMapOf<String, Any?>(
                "sysName" to sysNameCache.get("sysName").orElse("-")
            )
            if (event is InstanceStatusChangedEvent) {
                values["status"] = event.statusInfo
            } else if (event is InstanceInfoChangedEvent) {
                values["info"] = event.info
            }
            val httpRequest = if (certification != null && !certification.username.isNullOrBlank() && !certification.password.isNullOrBlank()) {
                HttpEntity<Map<String, Any?>>(
                    values,
                    CommonUtils.basicAuth(certification.username!!, certification.password!!),
                )
            } else {
                HttpEntity<Map<String, Any?>>(values)
            }
            val response: ResponseEntity<String> = restTemplate.exchange(
                harborProps.healthReportUrl,
                HttpMethod.POST,
                httpRequest
            )
            if (response.statusCode.is2xxSuccessful) {
                logger.debug("report health success!")
            } else {
                logger.error("report health failed! {}: {}", response.statusCode, response.statusCode.reasonPhrase)
            }
        }
    }
}