package com.sportsapp.infrastructure.persistence.user

import com.sportsapp.domain.user.RefreshTokenRepository
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class RedisRefreshTokenRepository(
    private val stringRedisTemplate: StringRedisTemplate,
) : RefreshTokenRepository {

    private val refreshTokenTtl: Duration = Duration.ofDays(14)

    private fun redisKey(refreshToken: String): String = "refreshtoken:$refreshToken"

    override fun save(userId: Long, refreshToken: String) {
        stringRedisTemplate.opsForValue().set(redisKey(refreshToken), userId.toString(), refreshTokenTtl)
    }

    override fun findUserIdByToken(refreshToken: String): Long? =
        stringRedisTemplate.opsForValue().get(redisKey(refreshToken))?.toLong()

    override fun invalidate(refreshToken: String) {
        stringRedisTemplate.unlink(redisKey(refreshToken))
    }
}
