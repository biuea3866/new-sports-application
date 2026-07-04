package com.sportsapp.infrastructure.featureflag.redis

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.domain.featureflag.gateway.FeatureFlagChangeBroadcaster
import java.time.ZonedDateTime
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

/**
 * `featureflag:changes` 채널 발행 구현 (`docs/feature-flag-redis-contract.md` §2).
 *
 * 발행 시각을 메시지 내부에서 직접 해결한다(호출부가 시간을 넘기지 않는다) — 전파 지연 측정 기준점.
 */
@Component
class RedisFeatureFlagChangeBroadcaster(
    private val stringRedisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) : FeatureFlagChangeBroadcaster {

    override fun broadcast(key: String) {
        val message = FeatureFlagChangeMessage(flagKey = key, occurredAt = ZonedDateTime.now())
        stringRedisTemplate.convertAndSend(FeatureFlagRedisKeys.CHANGE_CHANNEL, objectMapper.writeValueAsString(message))
    }
}
