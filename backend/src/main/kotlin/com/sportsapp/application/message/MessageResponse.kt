package com.sportsapp.application.message

import com.sportsapp.domain.message.Message
import java.time.ZonedDateTime

data class MessageResponse(
    val id: Long,
    val roomId: Long,
    val senderId: Long,
    val content: String,
    val sentAt: ZonedDateTime,
) {
    companion object {
        fun of(message: Message): MessageResponse = MessageResponse(
            id = message.id,
            roomId = message.roomId,
            senderId = message.userId,
            content = message.content,
            sentAt = message.createdAt,
        )
    }
}
