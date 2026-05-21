package com.sportsapp.application.message

data class SendMessageCommand(
    val roomId: Long,
    val senderId: Long,
    val content: String,
)
