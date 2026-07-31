package com.sportsapp.infrastructure.realtime

import com.sportsapp.domain.message.gateway.BroadcastMessage
import com.sportsapp.domain.message.gateway.ReadEvent
import com.sportsapp.domain.message.gateway.TypingEvent
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component

private const val REALTIME_ENABLED_PROPERTY = "chat.realtime.enabled"

/**
 * 이 인스턴스의 로컬 WebSocket 세션에만 STOMP 메시지를 전달한다 (W1-07 — Redis 릴레이 유무와
 * 무관하게 항상 수행되는 경로).
 *
 * [MessageBroadcastGatewayImpl](발신 — 로컬 전달 + Redis 발행)과 [RealtimeRelaySubscriber](다른
 * 인스턴스로부터 수신 — 로컬 전달만)가 공용한다. Subscriber 가 [MessageBroadcastGatewayImpl]을
 * 직접 호출하면 Redis 재발행이 다시 일어나 인스턴스 간 무한 왕복(ping-pong)이 발생하므로, 순수
 * 로컬 전달만 수행하는 이 클래스로 분리했다.
 *
 * `chat.realtime.enabled=false`면 등록되지 않는다 — 이 값이 false면 [WebSocketConfig]도 함께
 * 비활성화되어 `SimpMessagingTemplate` 빈 자체가 없으므로, 무조건 등록하면 컨텍스트 기동이
 * 실패한다(`MessageBroadcastGatewayImpl`과 동일한 가드).
 */
@Component
@ConditionalOnProperty(name = [REALTIME_ENABLED_PROPERTY], havingValue = "true", matchIfMissing = false)
class LocalStompBroadcaster(
    private val simpMessagingTemplate: SimpMessagingTemplate,
) {
    fun sendMessage(roomId: Long, message: BroadcastMessage) {
        simpMessagingTemplate.convertAndSend(destinationFor(roomId), message)
    }

    fun sendTyping(roomId: Long, event: TypingEvent) {
        simpMessagingTemplate.convertAndSend("${destinationFor(roomId)}/typing", event)
    }

    fun sendRead(roomId: Long, event: ReadEvent) {
        simpMessagingTemplate.convertAndSend("${destinationFor(roomId)}/read", event)
    }

    private fun destinationFor(roomId: Long): String = "/topic/rooms/$roomId"
}
