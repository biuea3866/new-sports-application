package com.sportsapp.infrastructure.persistence.booking

import com.sportsapp.domain.booking.BookingStatus
import com.sportsapp.domain.booking.Slot
import com.sportsapp.domain.booking.SlotRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class SlotRepositoryImpl(
    private val slotJpaRepository: SlotJpaRepository,
    private val bookingJpaRepository: BookingJpaRepository,
) : SlotRepository {

    override fun save(slot: Slot): Slot =
        slotJpaRepository.save(slot)

    override fun findById(id: Long): Slot? =
        slotJpaRepository.findByIdOrNull(id)

    override fun findByFacilityId(facilityId: String): List<Slot> =
        slotJpaRepository.findByFacilityIdAndDeletedAtIsNull(facilityId)

    override fun hasPendingOrConfirmedBooking(slotId: Long): Boolean =
        bookingJpaRepository.countBySlotIdAndStatusIn(
            slotId,
            listOf(BookingStatus.PENDING, BookingStatus.CONFIRMED),
        ) > 0

    override fun existsActiveByFacilityId(facilityId: String): Boolean =
        slotJpaRepository.existsByFacilityIdAndDeletedAtIsNull(facilityId)
}
