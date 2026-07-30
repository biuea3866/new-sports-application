package com.sportsapp.infrastructure.realtime

import com.sportsapp.domain.message.gateway.BroadcastMessage
import com.sportsapp.domain.message.gateway.MessageBroadcastGateway
import com.sportsapp.domain.message.gateway.ReadEvent
import com.sportsapp.domain.message.gateway.TypingEvent
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component

private const val REALTIME_ENABLED_PROPERTY = "chat.realtime.enabled"

/**
 * Simple Broker(`SimpMessagingTemplate`)로 `/topic/rooms/{roomId}[/typing|/read]` 를 발행한다.
 *
 * `chat.realtime.enabled=false` 면 등록되지 않는다 — 이 경우 [WebSocketConfig] 도 비활성화되어
 * `SimpMessagingTemplate` 빈 자체가 없으므로 함께 조건부 등록되어야 컨텍스트 기동이 안전하다.
 */
@Component
@ConditionalOnProperty(name = [REALTIME_ENABLED_PROPERTY], havingValue = "true", matchIfMissing = false)
class MessageBroadcastGatewayImpl(
    private val simpMessagingTemplate: SimpMessagingTemplate,
) : MessageBroadcastGateway {

    override fun broadcast(roomId: Long, message: BroadcastMessage) {
        simpMessagingTemplate.convertAndSend(destinationFor(roomId), message)
    }

    override fun broadcastTyping(roomId: Long, event: TypingEvent) {
        simpMessagingTemplate.convertAndSend("${destinationFor(roomId)}/typing", event)
    }

    override fun broadcastRead(roomId: Long, event: ReadEvent) {
        simpMessagingTemplate.convertAndSend("${destinationFor(roomId)}/read", event)
    }

    private fun destinationFor(roomId: Long): String = "/topic/rooms/$roomId"
}
