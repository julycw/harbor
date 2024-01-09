package julyww.harbor.persist.app

import julyww.harbor.persist.KEY_NAMESPACE
import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.data.redis.core.index.Indexed
import java.util.*

enum class UpdateState {
    Checking,
    Fail,
    Success
}

@RedisHash(KEY_NAMESPACE + "app_update_history")
class UpdateHistoryEntity(

    @Id
    var id: Long?,

    @Indexed
    var applicationId: Long,

    var updateTime: Date,

    var updateFileMd5: String,

    var state: UpdateState,

    var backupFilePath: String? = null

)

interface UpdateHistoryRepository : PagingAndSortingRepository<UpdateHistoryEntity, Long> {
    fun findByApplicationId(applicationId: Long): List<UpdateHistoryEntity>
    fun deleteByApplicationId(applicationId: Long)
}