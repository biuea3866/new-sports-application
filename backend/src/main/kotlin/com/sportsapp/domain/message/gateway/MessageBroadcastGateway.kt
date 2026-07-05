package com.sportsapp.domain.message.gateway

import java.time.ZonedDateTime

/**
 * 실시간 메시지 팬아웃 추상화 (domain interface).
 *
 * 구현체는 infrastructure 레이어(`MessageBroadcastGatewayImpl`)가 Simple Broker로 발행한다.
 * payload 는 raw String/Map 이 아닌 의미 있는 data class 로 타입화한다.
 */
interface MessageBroadcastGateway {
    fun broadcast(roomId: Long, message: BroadcastMessage)
    fun broadcastTyping(roomId: Long, event: TypingEvent)
    fun broadcastRead(roomId: Long, event: ReadEvent)
}

data class BroadcastMessage(
    val messageId: Long,
    val userId: Long,
    val content: String,
    val createdAt: ZonedDateTime,
)

data class TypingEvent(
    val userId: Long,
    val typing: Boolean,
)

data class ReadEvent(
    val userId: Long,
    val lastReadMessageId: Long,
)
