package com.sportsapp.infrastructure.security

import com.sportsapp.domain.user.JwtBlacklistStore
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class RedisJwtBlacklistStore(
    private val stringRedisTemplate: StringRedisTemplate,
) : JwtBlacklistStore {

    private fun blacklistKey(jti: String): String = "jwt:bl:$jti"

    override fun add(jti: String, ttl: Duration) {
        if (ttl.isZero || ttl.isNegative) return
        stringRedisTemplate.opsForValue().set(blacklistKey(jti), "1", ttl)
    }

    override fun isBlacklisted(jti: String): Boolean =
        stringRedisTemplate.hasKey(blacklistKey(jti))
}
