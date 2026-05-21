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

    private fun tokenKey(refreshToken: String): String = "refreshtoken:$refreshToken"
    private fun userKey(userId: Long): String = "refreshtoken:user:$userId"

    override fun save(userId: Long, refreshToken: String) {
        stringRedisTemplate.opsForValue().set(tokenKey(refreshToken), userId.toString(), refreshTokenTtl)
        stringRedisTemplate.opsForValue().set(userKey(userId), refreshToken, refreshTokenTtl)
    }

    override fun findUserIdByToken(refreshToken: String): Long? =
        stringRedisTemplate.opsForValue().get(tokenKey(refreshToken))?.toLong()

    override fun invalidate(refreshToken: String) {
        stringRedisTemplate.unlink(tokenKey(refreshToken))
    }

    override fun invalidateByUserId(userId: Long) {
        val refreshToken = stringRedisTemplate.opsForValue().get(userKey(userId)) ?: return
        stringRedisTemplate.unlink(tokenKey(refreshToken))
        stringRedisTemplate.unlink(userKey(userId))
    }
}
