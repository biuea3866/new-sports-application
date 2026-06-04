package com.sportsapp.domain.booking.service

import com.sportsapp.domain.booking.entity.Slot
import com.sportsapp.domain.booking.exception.SlotHasActiveBookingException
import com.sportsapp.domain.booking.repository.SlotRepository
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import java.time.ZonedDateTime
import org.springframework.stereotype.Service

@Service
class SlotDomainService(
    private val slotRepository: SlotRepository,
) {
    fun createSlot(
        ownerId: Long,
        facilityId: String,
        date: ZonedDateTime,
        timeRange: String,
        capacity: Int,
    ): Slot {
        val slot = Slot.create(
            facilityId = facilityId,
            date = date,
            timeRange = timeRange,
            capacity = capacity,
            ownerId = ownerId,
        )
        return slotRepository.save(slot)
    }

    fun updateSlot(
        requesterId: Long,
        slotId: Long,
        newTimeRange: String?,
        newCapacity: Int?,
    ): Slot {
        val slot = slotRepository.findById(slotId)
            ?: throw ResourceNotFoundException("Slot", slotId)
        slot.requireOwnedBy(requesterId)
        slot.applyUpdate(newTimeRange, newCapacity)
        return slotRepository.save(slot)
    }

    fun deleteSlot(requesterId: Long, slotId: Long) {
        val slot = slotRepository.findById(slotId)
            ?: throw ResourceNotFoundException("Slot", slotId)
        slot.requireOwnedBy(requesterId)
        if (slotRepository.hasPendingOrConfirmedBooking(slotId)) {
            throw SlotHasActiveBookingException(slotId)
        }
        slot.softDelete(requesterId)
        slotRepository.save(slot)
    }

    fun listSlots(facilityId: String): List<Slot> =
        slotRepository.findByFacilityId(facilityId)

    fun hasActiveSlotsByFacilityId(facilityId: String): Boolean =
        slotRepository.existsActiveByFacilityId(facilityId)

    fun countTodayByFacilityIds(facilityIds: List<String>): Long {
        if (facilityIds.isEmpty()) return 0L
        return slotRepository.countTodayByFacilityIds(facilityIds)
    }
}
