package com.sportsapp.infrastructure.realtime

import com.sportsapp.domain.message.gateway.BroadcastMessage
import com.sportsapp.domain.message.gateway.MessageBroadcastGateway
import com.sportsapp.domain.message.gateway.ReadEvent
import com.sportsapp.domain.message.gateway.TypingEvent
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

private const val REALTIME_ENABLED_PROPERTY = "chat.realtime.enabled"

/**
 * 이 인스턴스의 로컬 세션에 즉시 전달([LocalStompBroadcaster])하고, 동시에 다른 social
 * 인스턴스로 팬아웃하기 위해 Redis 릴레이([RealtimeRelayPublisher])에도 발행한다 (W1-07,
 * social 2 replica 전제 — 아키텍트 §9-3).
 *
 * `chat.realtime.enabled=false` 면 등록되지 않는다 — 이 경우 [WebSocketConfig] 도 비활성화되어
 * `SimpMessagingTemplate` 빈 자체가 없으므로 함께 조건부 등록되어야 컨텍스트 기동이 안전하다
 * (이 플래그의 기존 의미는 W1-07에서 변경하지 않는다 — 릴레이 자체의 on/off 는
 * [RealtimeRelayProperties]가 별도로 관리한다).
 */
@Component
@ConditionalOnProperty(name = [REALTIME_ENABLED_PROPERTY], havingValue = "true", matchIfMissing = false)
class MessageBroadcastGatewayImpl(
    private val localStompBroadcaster: LocalStompBroadcaster,
    private val realtimeRelayPublisher: RealtimeRelayPublisher,
    private val relayInstanceId: RelayInstanceId,
) : MessageBroadcastGateway {

    override fun broadcast(roomId: Long, message: BroadcastMessage) {
        localStompBroadcaster.sendMessage(roomId, message)
        realtimeRelayPublisher.publish(
            RealtimeRelayEnvelope.MessageBroadcast(
                senderInstanceId = relayInstanceId.value,
                roomId = roomId,
                messageId = message.messageId,
                userId = message.userId,
                content = message.content,
                createdAt = message.createdAt,
            ),
        )
    }

    override fun broadcastTyping(roomId: Long, event: TypingEvent) {
        localStompBroadcaster.sendTyping(roomId, event)
        realtimeRelayPublisher.publish(
            RealtimeRelayEnvelope.TypingBroadcast(
                senderInstanceId = relayInstanceId.value,
                roomId = roomId,
                userId = event.userId,
                typing = event.typing,
            ),
        )
    }

    override fun broadcastRead(roomId: Long, event: ReadEvent) {
        localStompBroadcaster.sendRead(roomId, event)
        realtimeRelayPublisher.publish(
            RealtimeRelayEnvelope.ReadBroadcast(
                senderInstanceId = relayInstanceId.value,
                roomId = roomId,
                userId = event.userId,
                lastReadMessageId = event.lastReadMessageId,
            ),
        )
    }
}
