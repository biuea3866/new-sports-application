package com.sportsapp.infrastructure.persistence.booking

import com.sportsapp.domain.booking.Booking
import com.sportsapp.domain.booking.BookingKpiQueryRepository
import com.sportsapp.domain.booking.BookingRepository
import com.sportsapp.domain.booking.BookingStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import java.time.ZonedDateTime


@Repository
class BookingRepositoryImpl(
    private val bookingJpaRepository: BookingJpaRepository,
    private val bookingKpiQueryRepository: BookingKpiQueryRepository,
) : BookingRepository {

    override fun save(booking: Booking): Booking =
        bookingJpaRepository.save(booking)

    override fun findById(id: Long): Booking? =
        bookingJpaRepository.findByIdOrNull(id)

    override fun findByUserIdAndStatus(userId: Long, status: BookingStatus): List<Booking> =
        bookingJpaRepository.findAllByUserIdAndStatus(userId, status)

    override fun findByUserIdAndStatusAndDateRange(
        userId: Long,
        status: BookingStatus?,
        from: ZonedDateTime?,
        to: ZonedDateTime?,
    ): List<Booking> =
        bookingJpaRepository.findByUserIdAndStatusAndDateRange(userId, status, from, to)

    override fun findPageByUserId(
        userId: Long,
        status: BookingStatus?,
        pageable: Pageable,
    ): Page<Booking> =
        bookingJpaRepository.findPageByUserId(userId, status, pageable)

    override fun countBySlotIdAndStatusIn(slotId: Long, statuses: List<BookingStatus>): Long =
        bookingJpaRepository.countBySlotIdAndStatusIn(slotId, statuses)

    override fun countConfirmedByOwnerUserIdAndDateRange(ownerUserId: Long, from: ZonedDateTime, to: ZonedDateTime): Long =
        bookingKpiQueryRepository.countConfirmedByOwnerUserIdAndDateRange(ownerUserId, from, to)

    override fun countRefundedByOwnerUserIdAndDateRange(ownerUserId: Long, from: ZonedDateTime, to: ZonedDateTime): Long =
        bookingKpiQueryRepository.countRefundedByOwnerUserIdAndDateRange(ownerUserId, from, to)

    override fun sumSlotCapacityByOwnerUserIdAndDateRange(ownerUserId: Long, from: ZonedDateTime, to: ZonedDateTime): Long =
        bookingKpiQueryRepository.sumSlotCapacityByOwnerUserIdAndDateRange(ownerUserId, from, to)

    override fun findTopFacilityIdsByOwnerUserIdAndDateRange(ownerUserId: Long, from: ZonedDateTime, to: ZonedDateTime, limit: Int): List<String> =
        bookingKpiQueryRepository.findTopFacilityIdsByOwnerUserIdAndDateRange(ownerUserId, from, to, limit)
}
