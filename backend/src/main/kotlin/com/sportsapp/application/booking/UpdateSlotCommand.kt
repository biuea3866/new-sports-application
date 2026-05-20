package com.sportsapp.application.booking

data class UpdateSlotCommand(
    val requesterId: Long,
    val slotId: Long,
    val timeRange: String?,
    val capacity: Int?,
)
