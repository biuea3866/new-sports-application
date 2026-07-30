package com.sportsapp.application.booking.dto

data class CloseSlotCommand(
    val requesterId: Long,
    val slotId: Long,
)
