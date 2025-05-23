package julyww.harbor.props

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "harbor")
class HarborProps {

    var backupDir: String = "/backups"

    var endpoint: String? = null

    var autoRegisterAppsOnStartUp: Boolean = false

    var autoRegisterAppsCron: String = "-"

    var deploymentBaseDir = "/app/home/"

    var updateFileDownloadUrlPrefix = "https://govfun.com:9990/deploy/"

    var healthReportUrl = "https://govfun.com:9990/health-report"

    var healthReportCertification: String = "default"

}