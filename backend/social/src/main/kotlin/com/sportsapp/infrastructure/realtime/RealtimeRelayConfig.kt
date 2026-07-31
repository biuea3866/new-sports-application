package com.sportsapp.infrastructure.realtime

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer

private const val REALTIME_ENABLED_PROPERTY = "chat.realtime.enabled"

/**
 * social 릴레이 채널 구독 리스너 컨테이너 배선 (W1-07).
 *
 * `platform`의 `FeatureFlagRedisPubSubConfig`와 동일한 근거로 `@Configuration`+`@Bean`을 쓴다 —
 * `RedisMessageListenerContainer`는 우리가 애노테이트할 수 없는 프레임워크 빈이라 컴포넌트 스캔
 * 대상이 아니다(no-bean-config-wiring 예외 — DomainService/UseCase 를 수동 배선하는 것이 아니다).
 *
 * `realtimeRelayMessageListenerContainer` 빈은 [RealtimeRelaySubscriber]에 의존하는데, 그
 * 빈은 `chat.realtime.enabled=false`면 등록되지 않으므로 이 빈도 같은 조건으로 가드한다
 * (미가드 시 플래그 OFF 상태에서 `UnsatisfiedDependencyException`으로 컨텍스트 기동이 실패한다).
 */
@Configuration
@EnableConfigurationProperties(RealtimeRelayProperties::class)
class RealtimeRelayConfig {

    @Bean
    @ConditionalOnProperty(name = [REALTIME_ENABLED_PROPERTY], havingValue = "true", matchIfMissing = false)
    fun realtimeRelayMessageListenerContainer(
        redisConnectionFactory: RedisConnectionFactory,
        realtimeRelaySubscriber: RealtimeRelaySubscriber,
        properties: RealtimeRelayProperties,
    ): RedisMessageListenerContainer {
        val container = RedisMessageListenerContainer()
        container.setConnectionFactory(redisConnectionFactory)
        container.addMessageListener(realtimeRelaySubscriber, ChannelTopic(properties.channel))
        return container
    }
}
