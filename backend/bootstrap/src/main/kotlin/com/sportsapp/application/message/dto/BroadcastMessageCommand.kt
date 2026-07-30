package com.sportsapp.application.message.dto

import java.time.ZonedDateTime

data class BroadcastMessageCommand(
    val roomId: Long,
    val messageId: Long,
    val senderId: Long,
    val content: String,
    val sentAt: ZonedDateTime,
)
