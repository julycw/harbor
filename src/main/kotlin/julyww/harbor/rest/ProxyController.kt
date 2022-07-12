package julyww.harbor.rest

import cn.trustway.nb.common.auth.annotation.auth.RequiresAuthentication
import com.netflix.appinfo.InstanceInfo
import com.netflix.discovery.EurekaClient
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import julyww.harbor.rest.global.TargetNotRegisteredException
import org.springframework.cloud.gateway.mvc.ProxyExchange
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.util.StringUtils
import org.springframework.web.bind.annotation.*
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
        return proxy.sensitive().uri("http://$ip:$port/$path").get()
    }

    @ApiOperation("Proxy Post")
    @PostMapping("/**")
    fun proxyPost(httpServletRequest: HttpServletRequest, proxy: ProxyExchange<ByteArray>): ResponseEntity<*> {
        val (ip, port) = targetIpAndPort(httpServletRequest)
        val path = proxy.path("/proxy/")
        return proxy.sensitive().uri("http://$ip:$port/$path").post()
    }

    @ApiOperation("Proxy Put")
    @PutMapping("/**")
    fun proxyPut(httpServletRequest: HttpServletRequest, proxy: ProxyExchange<ByteArray>): ResponseEntity<*> {
        val (ip, port) = targetIpAndPort(httpServletRequest)
        val path = proxy.path("/proxy/")
        return proxy.sensitive().uri("http://$ip:$port/$path").put()
    }

    @ApiOperation("Proxy Delete")
    @DeleteMapping("/**")
    fun proxyDelete(httpServletRequest: HttpServletRequest, proxy: ProxyExchange<ByteArray>): ResponseEntity<*> {
        val (ip, port) = targetIpAndPort(httpServletRequest)
        val path = proxy.path("/proxy/")
        return proxy.sensitive().uri("http://$ip:$port/$path").delete()
    }

    private fun targetIpAndPort(httpServletRequest: HttpServletRequest): IPAndPort {
        val instanceId = httpServletRequest.getHeader("Instance-Id")
        if (!StringUtils.hasText(instanceId)) throw TargetNotRegisteredException("proxy target id not provided")
        val httpHeaders = HttpHeaders()
        httpHeaders.set(HttpHeaders.AUTHORIZATION, httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION))
        return (eurekaClient.getInstancesById(instanceId).firstOrNull() as? InstanceInfo)?.let {
            IPAndPort(
                it.ipAddr, it.port.toString()
            )
        } ?: throw TargetNotRegisteredException("proxy target $instanceId not registered")
    }

}