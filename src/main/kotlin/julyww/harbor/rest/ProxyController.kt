package julyww.harbor.rest

import cn.trustway.nb.common.auth.annotation.auth.RequiresAuthentication
import cn.trustway.nb.common.auth.exception.app.AppException
import com.netflix.appinfo.InstanceInfo
import com.netflix.discovery.EurekaClient
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.cloud.gateway.mvc.ProxyExchange
import org.springframework.http.ResponseEntity
import org.springframework.util.StringUtils
import org.springframework.web.bind.annotation.*
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import javax.servlet.http.HttpServletRequest

data class IPAndPort(
    val ip: String,
    val port: String
)

@RequiresAuthentication
@Api(tags = ["服务接口代理"])
@RequestMapping("/proxy")
@RestController
class ActuatorProxyController(
    private val eurekaClient: EurekaClient
) {

    @ApiOperation("Proxy Get")
    @GetMapping("/**")
    fun proxyGet(httpServletRequest: HttpServletRequest, proxy: ProxyExchange<ByteArray>): ResponseEntity<*> {
        val (ip, port) = targetIpAndPort(httpServletRequest)
        val path = proxy.path("/proxy/")
        return proxy.sensitive().uri(convertUri(httpServletRequest, "http://$ip:$port/$path")).get()
    }

    @ApiOperation("Proxy Post")
    @PostMapping("/**")
    fun proxyPost(httpServletRequest: HttpServletRequest, proxy: ProxyExchange<ByteArray>): ResponseEntity<*> {
        val (ip, port) = targetIpAndPort(httpServletRequest)
        val path = proxy.path("/proxy/")
        return proxy.sensitive().uri(convertUri(httpServletRequest, "http://$ip:$port/$path")).post()
    }

    @ApiOperation("Proxy Put")
    @PutMapping("/**")
    fun proxyPut(httpServletRequest: HttpServletRequest, proxy: ProxyExchange<ByteArray>): ResponseEntity<*> {
        val (ip, port) = targetIpAndPort(httpServletRequest)
        val path = proxy.path("/proxy/")
        return proxy.sensitive().uri(convertUri(httpServletRequest, "http://$ip:$port/$path")).put()
    }

    @ApiOperation("Proxy Delete")
    @DeleteMapping("/**")
    fun proxyDelete(httpServletRequest: HttpServletRequest, proxy: ProxyExchange<ByteArray>): ResponseEntity<*> {
        val (ip, port) = targetIpAndPort(httpServletRequest)
        val path = proxy.path("/proxy/")
        return proxy.sensitive().uri(convertUri(httpServletRequest, "http://$ip:$port/$path")).delete()
    }

    @ApiOperation("Proxy Get")
    @GetMapping("/{instanceId}/**")
    fun proxyGetEx(
        @PathVariable instanceId: String,
        httpServletRequest: HttpServletRequest,
        proxy: ProxyExchange<ByteArray>
    ): ResponseEntity<*> {
        val (ip, port) = targetIpAndPort(instanceId)
        val path = proxy.path("/proxy/${instanceId}/")
        return proxy.sensitive().uri(convertUri(httpServletRequest, "http://$ip:$port/$path")).get()
    }

    @ApiOperation("Proxy Post")
    @PostMapping("/{instanceId}/**")
    fun proxyPostEx(
        @PathVariable instanceId: String,
        httpServletRequest: HttpServletRequest,
        proxy: ProxyExchange<ByteArray>
    ): ResponseEntity<*> {
        val (ip, port) = targetIpAndPort(instanceId)
        val path = proxy.path("/proxy/${instanceId}/")
        return proxy.sensitive().uri(convertUri(httpServletRequest, "http://$ip:$port/$path")).post()
    }

    @ApiOperation("Proxy Put")
    @PutMapping("/{instanceId}/**")
    fun proxyPutEx(
        @PathVariable instanceId: String,
        httpServletRequest: HttpServletRequest,
        proxy: ProxyExchange<ByteArray>
    ): ResponseEntity<*> {
        val (ip, port) = targetIpAndPort(instanceId)
        val path = proxy.path("/proxy/${instanceId}/")
        return proxy.sensitive().uri(convertUri(httpServletRequest, "http://$ip:$port/$path")).put()
    }

    @ApiOperation("Proxy Delete")
    @DeleteMapping("/{instanceId}/**")
    fun proxyDeleteEx(
        @PathVariable instanceId: String,
        httpServletRequest: HttpServletRequest,
        proxy: ProxyExchange<ByteArray>
    ): ResponseEntity<*> {
        val (ip, port) = targetIpAndPort(instanceId)
        val path = proxy.path("/proxy/${instanceId}/")
        return proxy.sensitive().uri(convertUri(httpServletRequest, "http://$ip:$port/$path")).delete()
    }

    private fun convertUri(httpServletRequest: HttpServletRequest, path: String): URI {
        return UriComponentsBuilder.fromHttpUrl(path)
            .query(httpServletRequest.queryString)
            .build().toUri()

    }

    private fun targetIpAndPort(httpServletRequest: HttpServletRequest): IPAndPort {
        val instanceId = httpServletRequest.getHeader("Instance-Id")
        return targetIpAndPort(instanceId)
    }

    private fun targetIpAndPort(instanceId: String?): IPAndPort {
        if (!StringUtils.hasText(instanceId)) throw AppException(400, "proxy target id not provided")
        return (eurekaClient.getInstancesById(instanceId).firstOrNull() as? InstanceInfo)?.let {
            IPAndPort(
                it.ipAddr, it.port.toString()
            )
        } ?: throw AppException(400, "proxy target $instanceId not registered")
    }

}