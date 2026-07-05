package com.sportsapp.application.message.dto

data class BroadcastTypingCommand(
    val roomId: Long,
    val userId: Long,
    val typing: Boolean,
)
