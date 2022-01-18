package julyww.harbor.persist.host

import julyww.harbor.persist.KEY_NAMESPACE
import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash
import org.springframework.data.repository.PagingAndSortingRepository

@RedisHash(KEY_NAMESPACE + "host")
data class HostEntity(

    @Id
    var id: Long?,

    var name: String,

    var ip: String?,

    var port: Int?,

    var username: String?,

    var password: String?

)

interface HostRepository : PagingAndSortingRepository<HostEntity, Long>