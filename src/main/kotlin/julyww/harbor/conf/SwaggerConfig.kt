package julyww.harbor.conf

import cn.trustway.nb.common.auth.autoconfig.security.TokenProperties
import julyww.harbor.HarborApplication
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import springfox.documentation.builders.ApiInfoBuilder
import springfox.documentation.builders.PathSelectors
import springfox.documentation.builders.RequestHandlerSelectors
import springfox.documentation.builders.RequestParameterBuilder
import springfox.documentation.service.ApiInfo
import springfox.documentation.service.ParameterType
import springfox.documentation.service.RequestParameter
import springfox.documentation.spi.DocumentationType
import springfox.documentation.spring.web.plugins.Docket

@Configuration
class SwaggerConfig(
    private val tokenProperties: TokenProperties
) {

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

    private fun group(
        name: String,
        basePackage: String = HarborApplication::class.java.packageName,
        pathPrefix: Collection<String>? = null
    ): Docket {
        var docket = Docket(DocumentationType.OAS_30)
            .groupName(name)
            .apiInfo(apiInfo())
            .globalRequestParameters(globalParams())
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

    private fun globalParams(): MutableList<RequestParameter> {
        val parameterBuilder = RequestParameterBuilder()
        parameterBuilder
            .name(tokenProperties.headerField)
            .description("用于身份校验的token")
            .`in`(ParameterType.HEADER)
            .required(false)
            .build()
        val globalParams: MutableList<RequestParameter> = ArrayList()
        globalParams.add(parameterBuilder.build())
        return globalParams
    }

}