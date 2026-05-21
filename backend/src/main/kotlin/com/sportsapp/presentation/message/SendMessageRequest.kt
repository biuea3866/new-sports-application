package com.sportsapp.presentation.message

import com.sportsapp.application.message.SendMessageCommand

data class SendMessageRequest(
    val content: String,
) {
    fun toCommand(roomId: Long, senderId: Long): SendMessageCommand =
        SendMessageCommand(roomId = roomId, senderId = senderId, content = content)
}
