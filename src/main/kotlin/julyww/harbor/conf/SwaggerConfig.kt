package julyww.harbor.conf

import julyww.harbor.HarborApplication
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.autoconfigure.endpoint.web.CorsEndpointProperties
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementPortType
import org.springframework.boot.actuate.endpoint.ExposableEndpoint
import org.springframework.boot.actuate.endpoint.web.EndpointLinksResolver
import org.springframework.boot.actuate.endpoint.web.EndpointMapping
import org.springframework.boot.actuate.endpoint.web.EndpointMediaTypes
import org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier
import org.springframework.boot.actuate.endpoint.web.annotation.ControllerEndpointsSupplier
import org.springframework.boot.actuate.endpoint.web.annotation.ServletEndpointsSupplier
import org.springframework.boot.actuate.endpoint.web.servlet.WebMvcEndpointHandlerMapping
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.util.StringUtils
import springfox.documentation.builders.ApiInfoBuilder
import springfox.documentation.builders.PathSelectors
import springfox.documentation.builders.RequestHandlerSelectors
import springfox.documentation.service.ApiInfo
import springfox.documentation.spi.DocumentationType
import springfox.documentation.spring.web.plugins.Docket


@Configuration
class SwaggerConfig {

    @Value("\${server.servlet.context-path}")
    private lateinit var context: String

    @Bean
    fun frequentAskQuestDocket() = group("接口")

    @Bean
    fun apiInfo(): ApiInfo {
        return ApiInfoBuilder()
            .title("Docker容器管理模块")
            .description("Docker容器管理模块")
            .version("1.0")
            .build()
    }

    @Bean
    fun webEndpointServletHandlerMapping(
        webEndpointsSupplier: WebEndpointsSupplier,
        servletEndpointsSupplier: ServletEndpointsSupplier, controllerEndpointsSupplier: ControllerEndpointsSupplier,
        endpointMediaTypes: EndpointMediaTypes?, corsProperties: CorsEndpointProperties,
        webEndpointProperties: WebEndpointProperties, environment: Environment
    ): WebMvcEndpointHandlerMapping {
        val allEndpoints: MutableList<ExposableEndpoint<*>> = ArrayList()
        val webEndpoints = webEndpointsSupplier.endpoints
        allEndpoints.addAll(webEndpoints)
        allEndpoints.addAll(servletEndpointsSupplier.endpoints)
        allEndpoints.addAll(controllerEndpointsSupplier.endpoints)
        val basePath = webEndpointProperties.basePath
        val endpointMapping = EndpointMapping(basePath)
        val shouldRegisterLinksMapping =
            webEndpointProperties.discovery.isEnabled && (StringUtils.hasText(basePath) || ManagementPortType.get(
                environment
            ) == ManagementPortType.DIFFERENT)
        return WebMvcEndpointHandlerMapping(
            endpointMapping, webEndpoints, endpointMediaTypes,
            corsProperties.toCorsConfiguration(), EndpointLinksResolver(allEndpoints, basePath),
            shouldRegisterLinksMapping, null
        )
    }

    private fun group(
        name: String,
        basePackage: String = HarborApplication::class.java.packageName,
        pathPrefix: Collection<String>? = null
    ): Docket {
        var docket = Docket(DocumentationType.OAS_30)
            .groupName(name)
            .apiInfo(apiInfo())
            .select()
            .apis(RequestHandlerSelectors.basePackage(basePackage))
        pathPrefix?.let {
            docket = docket.paths(let {
                var predicate = PathSelectors.none()
                pathPrefix.forEach {
                    predicate = predicate.or(PathSelectors.ant("/$context/$it/**"))
                }
                predicate
            })
        }
        return docket.build()
    }

}