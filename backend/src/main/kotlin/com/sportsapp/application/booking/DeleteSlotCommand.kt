package com.sportsapp.application.booking

data class DeleteSlotCommand(
    val requesterId: Long,
    val slotId: Long,
)
