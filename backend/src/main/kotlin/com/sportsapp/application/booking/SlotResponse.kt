package com.sportsapp.application.booking

import com.sportsapp.domain.booking.Slot
import java.time.ZonedDateTime

data class SlotResponse(
    val id: Long,
    val facilityId: String,
    val date: ZonedDateTime,
    val timeRange: String,
    val capacity: Int,
    val ownerId: Long,
) {
    companion object {
        fun of(slot: Slot): SlotResponse = SlotResponse(
            id = slot.id,
            facilityId = slot.facilityId,
            date = slot.date,
            timeRange = slot.timeRange,
            capacity = slot.capacity,
            ownerId = slot.ownerId,
        )
    }
}
