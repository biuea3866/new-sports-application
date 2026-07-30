package com.sportsapp.presentation.booking.dto.request

import com.sportsapp.application.booking.dto.CreateSlotCommand
import java.time.ZonedDateTime

data class CreateSlotRequest(
    val date: ZonedDateTime,
    val timeRange: String,
    val capacity: Int,
) {
    fun toCommand(ownerId: Long, facilityId: String): CreateSlotCommand = CreateSlotCommand(
        ownerId = ownerId,
        facilityId = facilityId,
        date = date,
        timeRange = timeRange,
        capacity = capacity,
    )
}
