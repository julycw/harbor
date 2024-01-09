package julyww.harbor.props

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "harbor")
class HarborProps {

    var backupDir: String = "/backups"

    var endpoint: String? = null
}