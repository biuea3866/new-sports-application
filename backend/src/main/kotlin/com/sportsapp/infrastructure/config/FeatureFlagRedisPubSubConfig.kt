package com.sportsapp.infrastructure.config

import com.sportsapp.domain.featureflag.repository.FeatureFlagRepository
import com.sportsapp.infrastructure.featureflag.redis.FeatureFlagChangeSubscriber
import com.sportsapp.infrastructure.featureflag.redis.FeatureFlagRedisKeys
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer

/**
 * FeatureFlag 변경 전파 pub/sub 리스너 설정 — 레포 최초 도입되는 `RedisMessageListenerContainer`.
 *
 * `featureflag:changes` 단일 채널만 구독한다(`docs/feature-flag-redis-contract.md` §2).
 * `@ConditionalOnBean(FeatureFlagRepository::class)`: `FeatureFlagChangeSubscriber`(BE-02 런타임 의존)와
 * 동일한 조건으로 묶어, 이 워크트리처럼 BE-02 구현체가 없는 상태에서 애플리케이션 컨텍스트가
 * 실패 없이 기동되게 한다.
 */
@Configuration
@ConditionalOnBean(FeatureFlagRepository::class)
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
