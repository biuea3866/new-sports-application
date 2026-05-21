package com.sportsapp.presentation.booking

import com.sportsapp.application.booking.UpdateSlotCommand

data class UpdateSlotRequest(
    val timeRange: String?,
    val capacity: Int?,
) {
    fun toCommand(requesterId: Long, slotId: Long): UpdateSlotCommand = UpdateSlotCommand(
        requesterId = requesterId,
        slotId = slotId,
        timeRange = timeRange,
        capacity = capacity,
    )
}
