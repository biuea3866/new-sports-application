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

    private fun redisKey(userId: Long): String = "refresh:$userId"

    override fun save(userId: Long, refreshToken: String) {
        stringRedisTemplate.opsForValue().set(redisKey(userId), refreshToken, refreshTokenTtl)
    }

    override fun find(userId: Long): String? =
        stringRedisTemplate.opsForValue().get(redisKey(userId))

    override fun remove(userId: Long) {
        stringRedisTemplate.unlink(redisKey(userId))
    }
}
