package com.sportsapp.application.message.dto

data class BroadcastReadCommand(
    val roomId: Long,
    val userId: Long,
    val lastReadMessageId: Long,
)
