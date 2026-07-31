package com.sportsapp.infrastructure.realtime

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.domain.message.gateway.BroadcastMessage
import com.sportsapp.domain.message.gateway.ReadEvent
import com.sportsapp.domain.message.gateway.TypingEvent
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.connection.MessageListener
import org.springframework.stereotype.Component

private const val REALTIME_ENABLED_PROPERTY = "chat.realtime.enabled"

/**
 * [RealtimeRelayProperties.channel] 구독자 (W1-07) — 다른 social 인스턴스가 발행한 메시지를 이
 * 인스턴스의 로컬 세션에만 전달한다.
 *
 * 자기 인스턴스([RelayInstanceId])가 발행한 envelope 는 [MessageBroadcastGatewayImpl]이 이미
 * 로컬 전달을 완료한 상태이므로 폐기한다(자기 발행 메시지 무시 — 중복 전달 방지).
 *
 * `chat.realtime.enabled=false`면 등록되지 않는다 — [LocalStompBroadcaster] 의존과 동일한 가드.
 */
@Component
@ConditionalOnProperty(name = [REALTIME_ENABLED_PROPERTY], havingValue = "true", matchIfMissing = false)
class RealtimeRelaySubscriber(
    private val localStompBroadcaster: LocalStompBroadcaster,
    private val relayInstanceId: RelayInstanceId,
    @Qualifier("realtimeRelayObjectMapper") private val objectMapper: ObjectMapper,
) : MessageListener {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun onMessage(message: Message, pattern: ByteArray?) {
        val envelope = deserialize(message) ?: return
        if (envelope.senderInstanceId == relayInstanceId.value) return
        deliverLocally(envelope)
    }

    private fun deserialize(message: Message): RealtimeRelayEnvelope? =
        runCatching {
            objectMapper.readValue(message.body, RealtimeRelayEnvelope::class.java)
        }.getOrElse { exception ->
            log.warn("event=realtime-relay-message-invalid source=social message={}", exception.message)
            null
        }

    private fun deliverLocally(envelope: RealtimeRelayEnvelope) {
        when (envelope) {
            is RealtimeRelayEnvelope.MessageBroadcast ->
                localStompBroadcaster.sendMessage(
                    envelope.roomId,
                    BroadcastMessage(
                        messageId = envelope.messageId,
                        userId = envelope.userId,
                        content = envelope.content,
                        createdAt = envelope.createdAt,
                    ),
                )
            is RealtimeRelayEnvelope.TypingBroadcast ->
                localStompBroadcaster.sendTyping(
                    envelope.roomId,
                    TypingEvent(userId = envelope.userId, typing = envelope.typing),
                )
            is RealtimeRelayEnvelope.ReadBroadcast ->
                localStompBroadcaster.sendRead(
                    envelope.roomId,
                    ReadEvent(userId = envelope.userId, lastReadMessageId = envelope.lastReadMessageId),
                )
        }
    }
}
