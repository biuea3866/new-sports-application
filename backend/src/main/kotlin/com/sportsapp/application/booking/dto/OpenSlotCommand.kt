package com.sportsapp.application.booking.dto

data class OpenSlotCommand(
    val requesterId: Long,
    val slotId: Long,
)
