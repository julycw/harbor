package julyww.harbor.conf

import cn.trustway.nb.common.auth.autoconfig.security.TokenProperties
import cn.trustway.nb.util.HttpServletUtil
import feign.RequestInterceptor
import feign.RequestTemplate

class AuthFeignClientConfig(
    private val tokenProperties: TokenProperties
) : RequestInterceptor {
    override fun apply(requestTemplate: RequestTemplate) {
        HttpServletUtil.getRequest()?.let { request ->
            requestTemplate.header(
                tokenProperties.headerField,
                request.getHeader(tokenProperties.headerField)
            )
        }
    }
}