package com.sportsapp.application.booking.dto

data class UpdateSlotCommand(
    val requesterId: Long,
    val slotId: Long,
    val timeRange: String?,
    val capacity: Int?,
)
