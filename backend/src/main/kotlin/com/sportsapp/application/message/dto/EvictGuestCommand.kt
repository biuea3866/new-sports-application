package com.sportsapp.application.message.dto

data class EvictGuestCommand(
    val roomId: Long,
    val userId: Long,
    val requesterId: Long,
)
