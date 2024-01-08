package julyww.harbor.core.certification.persist

import julyww.harbor.persist.KEY_NAMESPACE
import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash
import org.springframework.data.repository.PagingAndSortingRepository


enum class CertificationType {
    UsernamePassword
}

@RedisHash(KEY_NAMESPACE + "cert")
data class CertificationEntity(

    @Id
    var id: String,

    var name: String?,

    var type: CertificationType,

    var username: String?,

    var password: String?

)

interface CertificationRepository : PagingAndSortingRepository<CertificationEntity, String>