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

    var certificationId: String?,

    var basicAuthUsername: String?,

    var basicAuthPassword: String?,

    var version: String?,

    var latestUpdateTime: Date?,

    var autoRestart: Boolean = true,

    // 强制md5校验
    var checkMd5: Boolean? = null,

    // 定时重启
    var scheduleRestart: Boolean? = false,

    // 重启时间
    var restartAt: String? = null,

    // 自动更新
    var scheduleUpdate: Boolean? = false,

    // 自动更新时间
    var updateAt: String? = null,

    // 点位
    var endpoint: String? = null
)

interface AppRepository : PagingAndSortingRepository<AppEntity, Long>