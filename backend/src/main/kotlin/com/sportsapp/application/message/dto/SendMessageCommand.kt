package com.sportsapp.application.message.dto

data class SendMessageCommand(
    val roomId: Long,
    val senderId: Long,
    val content: String,
)
