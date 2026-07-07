package com.sportsapp.infrastructure.booking.mysql

import com.sportsapp.domain.booking.entity.BookingStatus
import com.sportsapp.domain.booking.entity.Slot
import com.sportsapp.domain.booking.repository.SlotRepository
import java.time.ZonedDateTime
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

    override fun findForUpdateById(id: Long): Slot? =
        slotJpaRepository.findForUpdateById(id)

    override fun findByFacilityId(facilityId: String, programId: Long?): List<Slot> =
        slotJpaRepository.findByFacilityId(facilityId, programId)

    override fun hasPendingOrConfirmedBooking(slotId: Long): Boolean =
        bookingJpaRepository.countBySlotIdAndStatusIn(
            slotId,
            listOf(BookingStatus.PENDING, BookingStatus.CONFIRMED),
        ) > 0

    override fun existsActiveByFacilityId(facilityId: String): Boolean =
        slotJpaRepository.existsByFacilityIdAndDeletedAtIsNull(facilityId)

    override fun countTodayByFacilityIds(facilityIds: List<String>): Long {
        val startOfToday = ZonedDateTime.now().toLocalDate().atStartOfDay(ZonedDateTime.now().zone)
        val startOfTomorrow = startOfToday.plusDays(1)
        return slotJpaRepository.countByFacilityIdInAndDeletedAtIsNullAndDateGreaterThanEqualAndDateLessThan(
            facilityIds,
            startOfToday,
            startOfTomorrow,
        )
    }
}
