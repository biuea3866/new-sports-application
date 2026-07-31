package com.sportsapp.infrastructure.config

import com.sportsapp.infrastructure.featureflag.redis.FeatureFlagChangeSubscriber
import com.sportsapp.infrastructure.featureflag.redis.FeatureFlagRedisKeys
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer

/**
 * FeatureFlag 변경 전파 pub/sub 리스너 설정 — 레포 최초 도입되는 `RedisMessageListenerContainer`.
 *
 * `featureflag:changes` 단일 채널만 구독한다(`docs/feature-flag-redis-contract.md` §2).
 */
@Configuration
class FeatureFlagRedisPubSubConfig {

    @Bean
    fun featureFlagRedisMessageListenerContainer(
        redisConnectionFactory: RedisConnectionFactory,
        featureFlagChangeSubscriber: FeatureFlagChangeSubscriber,
    ): RedisMessageListenerContainer {
        val container = RedisMessageListenerContainer()
        container.setConnectionFactory(redisConnectionFactory)
        container.addMessageListener(featureFlagChangeSubscriber, ChannelTopic(FeatureFlagRedisKeys.CHANGE_CHANNEL))
        return container
    }
}
