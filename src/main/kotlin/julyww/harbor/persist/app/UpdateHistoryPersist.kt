package julyww.harbor.persist.app

import cn.hutool.crypto.SecureUtil
import io.swagger.annotations.ApiModelProperty
import julyww.harbor.persist.KEY_NAMESPACE
import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.data.redis.core.index.Indexed
import java.nio.file.Files
import java.nio.file.Path
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

) {


    @get:ApiModelProperty("备份文件是否存在")
    val backUpFileExist: Boolean by lazy {
        try {
            if (backupFilePath.isNullOrBlank()) {
                false
            } else if (!Files.exists(Path.of(backupFilePath!!))) {
                false
            } else true
        } catch (_: Exception) {
            false
        }
    }

    @get:ApiModelProperty("是否可进行回滚")
    val rollbackAble: Boolean by lazy {
        if (!backUpFileExist) {
            false
        } else {
            updateFileMd5 == SecureUtil.md5().digestHex(Path.of(backupFilePath!!).toFile())
        }
    }

}

interface UpdateHistoryRepository : PagingAndSortingRepository<UpdateHistoryEntity, Long> {
    fun findByApplicationId(applicationId: Long): List<UpdateHistoryEntity>
    fun deleteByApplicationId(applicationId: Long)
}