package com.sportsapp.infrastructure.realtime

import com.sportsapp.domain.message.gateway.BroadcastMessage
import com.sportsapp.domain.message.gateway.MessageBroadcastGateway
import com.sportsapp.domain.message.gateway.ReadEvent
import com.sportsapp.domain.message.gateway.TypingEvent
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

private const val REALTIME_ENABLED_PROPERTY = "chat.realtime.enabled"

/**
 * `chat.realtime.enabled=false`(기본값) 일 때의 [MessageBroadcastGateway] 무동작 구현체 (BE-04 정정).
 *
 * `MessageDomainService` 가 항상 하나의 `MessageBroadcastGateway` 빈을 요구하므로(도메인 서비스는
 * 기능 플래그와 무관하게 항상 활성), 플래그 OFF 상태에서도 컨텍스트가 기동되도록 no-op 구현을 등록한다.
 * 이 상태에서는 `ChatStompController`/`MessageBroadcastEventWorker` 자체가 비활성화되어
 * `broadcastMessage`/`broadcastTyping` 이 호출되지 않으므로 실질적으로 사용되지 않는다.
 */
@Component
@ConditionalOnProperty(name = [REALTIME_ENABLED_PROPERTY], havingValue = "false", matchIfMissing = true)
class NoOpMessageBroadcastGateway : MessageBroadcastGateway {
    override fun broadcast(roomId: Long, message: BroadcastMessage) = Unit

    override fun broadcastTyping(roomId: Long, event: TypingEvent) = Unit

    override fun broadcastRead(roomId: Long, event: ReadEvent) = Unit
}
