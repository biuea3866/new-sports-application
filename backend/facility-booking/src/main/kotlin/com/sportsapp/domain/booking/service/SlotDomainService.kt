package com.sportsapp.domain.booking.service

import com.sportsapp.domain.booking.entity.Slot
import com.sportsapp.domain.booking.exception.SlotHasActiveBookingException
import com.sportsapp.domain.booking.gateway.FacilityOwnershipGateway
import com.sportsapp.domain.booking.repository.SlotRepository
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import java.time.ZonedDateTime
import org.springframework.stereotype.Service

@Service
class SlotDomainService(
    private val slotRepository: SlotRepository,
    private val facilityOwnershipGateway: FacilityOwnershipGateway,
) {
    fun createSlot(
        ownerId: Long,
        facilityId: String,
        date: ZonedDateTime,
        timeRange: String,
        capacity: Int,
    ): Slot = createSlot(
        ownerId = ownerId,
        facilityId = facilityId,
        date = date,
        timeRange = timeRange,
        capacity = capacity,
        programId = null,
    )

    fun createSlot(
        ownerId: Long,
        facilityId: String,
        date: ZonedDateTime,
        timeRange: String,
        capacity: Int,
        programId: Long?,
    ): Slot {
        facilityOwnershipGateway.requireOwner(facilityId, ownerId)
        val slot = Slot.create(
            facilityId = facilityId,
            date = date,
            timeRange = timeRange,
            capacity = capacity,
            ownerId = ownerId,
            programId = programId,
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

    fun closeSlot(requesterId: Long, slotId: Long): Slot {
        val slot = slotRepository.findById(slotId)
            ?: throw ResourceNotFoundException("Slot", slotId)
        slot.close(requesterId)
        return slotRepository.save(slot)
    }

    fun openSlot(requesterId: Long, slotId: Long): Slot {
        val slot = slotRepository.findById(slotId)
            ?: throw ResourceNotFoundException("Slot", slotId)
        slot.open(requesterId)
        return slotRepository.save(slot)
    }

    fun listSlots(facilityId: String, programId: Long?): List<Slot> =
        slotRepository.findByFacilityId(facilityId, programId)

    fun countTodayByFacilityIds(facilityIds: List<String>): Long {
        if (facilityIds.isEmpty()) return 0L
        return slotRepository.countTodayByFacilityIds(facilityIds)
    }

    /** [slotId] 슬롯을 조회한다. 없으면 null을 반환한다(예외 전파 금지 — 소비자 ACL 계약). */
    fun findBy(slotId: Long): Slot? = slotRepository.findById(slotId)

    /** [facilityId] 시설에 활성 슬롯이 존재하는지 조회한다. */
    fun hasActiveSlots(facilityId: String): Boolean = slotRepository.existsActiveByFacilityId(facilityId)
}
