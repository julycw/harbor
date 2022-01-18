package julyww.harbor.persist.app

import julyww.harbor.persist.KEY_NAMESPACE
import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash
import org.springframework.data.repository.PagingAndSortingRepository
import java.util.*

@RedisHash(KEY_NAMESPACE + "application")
data class AppEntity(

    @Id
    var id: Long?,

    var name: String,

    var containerId: String?,

    var md5: String?,

    var downloadAppUrl: String?,

    var localAppPath: String?,

    var basicAuthUsername: String?,

    var basicAuthPassword: String?,

    var version: String?,

    var latestUpdateTime: Date?
)

interface AppRepository : PagingAndSortingRepository<AppEntity, Long>