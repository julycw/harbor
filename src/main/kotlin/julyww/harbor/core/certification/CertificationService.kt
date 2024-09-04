package julyww.harbor.core.certification

import julyww.harbor.core.certification.encrypt.JasyptUtils
import julyww.harbor.core.certification.persist.CertificationEntity
import julyww.harbor.core.certification.persist.CertificationRepository
import julyww.harbor.core.certification.persist.CertificationType
import org.springframework.stereotype.Service
import java.util.*

data class CertificationDTO(
    var id: String?,
    var name: String?,
    var type: CertificationType,
    var username: String? = null,
    var password: String? = null
)

@Service
class CertificationService(
    private val certificationRepository: CertificationRepository
) {

    private final val password = "harbor"

    fun listAll(): List<CertificationDTO> {
        return certificationRepository.findAll().toList().map { toDTO(it) }
    }

    fun save(dto: CertificationDTO): CertificationDTO {
        if (dto.id.isNullOrBlank()) {
            dto.id = UUID.randomUUID().toString().replace("-", "")
        }

        if (!dto.username.isNullOrBlank()) {
            dto.username = JasyptUtils.encrypt(dto.username!!, password)
        }

        if (!dto.password.isNullOrBlank()) {
            dto.password = JasyptUtils.encrypt(dto.password!!, password)
        }

        certificationRepository.save(
            CertificationEntity(
                id = dto.id!!,
                name = dto.name,
                type = dto.type,
                username = dto.username?.let { JasyptUtils.encrypt(it, password) },
                password = dto.password?.let { JasyptUtils.encrypt(it, password) },
            )
        )

        return dto
    }

    fun delete(id: String) {
        certificationRepository.deleteById(id)
    }

    fun findById(id: String): CertificationDTO? {
        return certificationRepository.findById(id).map { entity -> toDTO(entity) }.orElse(null)
    }

    private fun toDTO(entity: CertificationEntity): CertificationDTO {
        return CertificationDTO(
            id = entity.id,
            name = entity.name,
            type = entity.type,
            username = entity.username?.let { JasyptUtils.decrypt(it, password) },
            password = entity.password?.let { JasyptUtils.decrypt(it, password) },
        )
    }

}