package com.sportsapp.presentation.booking.dto.response

import com.sportsapp.domain.booking.entity.Slot
import com.sportsapp.domain.booking.entity.SlotStatus
import java.time.ZonedDateTime

data class SlotResponse(
    val id: Long,
    val facilityId: String,
    val date: ZonedDateTime,
    val timeRange: String,
    val capacity: Int,
    val ownerId: Long,
    val status: SlotStatus,
    val programId: Long?,
) {
    companion object {
        fun of(slot: Slot): SlotResponse = SlotResponse(
            id = slot.id,
            facilityId = slot.facilityId,
            date = slot.date,
            timeRange = slot.timeRange,
            capacity = slot.capacity,
            ownerId = slot.ownerId,
            status = slot.status,
            programId = slot.programId,
        )
    }
}
