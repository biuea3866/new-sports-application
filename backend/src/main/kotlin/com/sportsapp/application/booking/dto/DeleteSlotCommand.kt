package com.sportsapp.application.booking.dto

data class DeleteSlotCommand(
    val requesterId: Long,
    val slotId: Long,
)
