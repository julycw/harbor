package julyww.harbor.persist

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

const val KEY_NAMESPACE = "harbor:"

@Component
class IdGenerator(
    private val redisTemplate: StringRedisTemplate
) {

    fun next(): Long {
        return redisTemplate.opsForValue().increment(KEY_NAMESPACE + "next-id") ?: 0
    }
}