package com.sportsapp.presentation.message.stomp

import com.sportsapp.application.message.dto.SendMessageCommand
import com.sportsapp.application.message.usecase.SendMessageUseCase
import com.sportsapp.domain.message.gateway.MessageBroadcastGateway
import com.sportsapp.domain.message.gateway.TypingEvent
import java.security.Principal
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Controller

private const val REALTIME_ENABLED_PROPERTY = "chat.realtime.enabled"

/**
 * STOMP 진입점 (FR-6). `/app/rooms/{roomId}/send` 는 [SendMessageUseCase] 를 경유해 영속화하고,
 * (AFTER_COMMIT 브로드캐스트는 [com.sportsapp.presentation.message.worker.MessageBroadcastEventWorker] 담당)
 * `/app/rooms/{roomId}/typing` 은 영속화가 필요 없는 실시간 신호라 [MessageBroadcastGateway] 로 즉시 팬아웃한다.
 *
 * `chat.realtime.enabled=false` 면 STOMP 메시지 처리 인프라([WebSocketConfig])가 등록되지 않아
 * 이 컨트롤러도 함께 비활성화되어야 컨텍스트 기동이 안전하다.
 */
@Controller
@ConditionalOnProperty(name = [REALTIME_ENABLED_PROPERTY], havingValue = "true", matchIfMissing = false)
class ChatStompController(
    private val sendMessageUseCase: SendMessageUseCase,
    private val messageBroadcastGateway: MessageBroadcastGateway,
) {

    @MessageMapping("/rooms/{roomId}/send")
    fun send(
        @DestinationVariable roomId: Long,
        @Payload request: StompSendMessageRequest,
        principal: Principal,
    ) {
        sendMessageUseCase.execute(request.toCommand(roomId, principal.userId()))
    }

    @MessageMapping("/rooms/{roomId}/typing")
    fun typing(
        @DestinationVariable roomId: Long,
        @Payload request: StompTypingRequest,
        principal: Principal,
    ) {
        messageBroadcastGateway.broadcastTyping(
            roomId,
            TypingEvent(userId = principal.userId(), typing = request.typing),
        )
    }

    private fun Principal.userId(): Long = name.toLong()
}

data class StompSendMessageRequest(val content: String) {
    fun toCommand(roomId: Long, senderId: Long): SendMessageCommand =
        SendMessageCommand(roomId = roomId, senderId = senderId, content = content)
}

data class StompTypingRequest(val typing: Boolean)
