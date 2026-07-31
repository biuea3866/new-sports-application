package com.sportsapp.infrastructure.realtime

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

/**
 * social 전용 Redis 채널([RealtimeRelayProperties.channel])에 [RealtimeRelayEnvelope]를 발행한다
 * (W1-07).
 *
 * `chat.realtime.relay.enabled=false`면 발행을 건너뛰어 로컬 전달만 남는 상태로 즉시 복귀한다
 * (티켓 롤백 절 — [RealtimeRelayProperties] 문서 참고).
 *
 * Redis 발행 실패(연결 단절 등)는 로그만 남기고 삼킨다 — [MessageBroadcastGatewayImpl]이 로컬
 * 세션 전달을 이미 먼저 수행했으므로, 여기서 예외를 전파하면 정상 동작한 로컬 전달까지 호출부
 * (UseCase/EventWorker)에서 실패로 오염된다. 이 인스턴스의 로컬 세션 전달은 Redis 연결 상태와
 * 무관하게 계속 동작한다(부분 저하 — 실패 경로).
 */
@Component
@EnableConfigurationProperties(RealtimeRelayProperties::class)
class RealtimeRelayPublisher(
    private val stringRedisTemplate: StringRedisTemplate,
    @Qualifier("realtimeRelayObjectMapper") private val objectMapper: ObjectMapper,
    private val properties: RealtimeRelayProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun publish(envelope: RealtimeRelayEnvelope) {
        if (!properties.enabled) return
        runCatching {
            stringRedisTemplate.convertAndSend(properties.channel, objectMapper.writeValueAsString(envelope))
        }.onFailure { exception ->
            log.warn(
                "event=realtime-relay-publish-failed source=social channel={} roomId={} message={}",
                properties.channel,
                envelope.roomId,
                exception.message,
            )
        }
    }
}
