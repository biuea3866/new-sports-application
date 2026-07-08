package com.sportsapp.presentation.message.dto.request

import com.sportsapp.application.message.dto.SendMessageCommand
import jakarta.validation.constraints.NotBlank

data class SendMessageRequest(
    @field:NotBlank(message = "content must not be blank")
    val content: String,
) {
    fun toCommand(roomId: Long, senderId: Long): SendMessageCommand =
        SendMessageCommand(roomId = roomId, senderId = senderId, content = content)
}
