package julyww.harbor.core.host

import julyww.harbor.common.PageResult
import julyww.harbor.persist.IdGenerator
import julyww.harbor.persist.host.HostEntity
import julyww.harbor.persist.host.HostRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class HostService(
    private val hostRepository: HostRepository,
    private val idGenerator: IdGenerator
) {


    fun findById(id: Long?): HostEntity? {
        return hostRepository.findByIdOrNull(id)
    }


    fun findByIP(ip: String): HostEntity? {
        return hostRepository.findFirstByIp(ip)
    }

    fun list(): PageResult<HostEntity> {
        val list = hostRepository.findAll()
        return PageResult(
            list = list.toList(),
            total = hostRepository.count()
        )
    }

    fun save(entity: HostEntity): Long? {
        return if (entity.id == null) {
            entity.id = idGenerator.next()
            hostRepository.save(entity).id
        } else {
            hostRepository.findByIdOrNull(entity.id!!)?.let {
                it.name = entity.name
                it.ip = entity.ip
                it.port = entity.port
                it.username = entity.username
                it.password = entity.password
                hostRepository.save(it)
            }
            entity.id
        }
    }

    fun delete(id: Long) {
        hostRepository.deleteById(id)
    }
}