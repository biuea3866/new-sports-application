package com.sportsapp.infrastructure.booking.mysql

import com.sportsapp.domain.booking.dto.BookingExpiryCandidate
import com.sportsapp.domain.booking.entity.Booking
import com.sportsapp.domain.booking.entity.BookingStatus
import com.sportsapp.domain.booking.repository.BookingKpiQueryRepository
import com.sportsapp.domain.booking.repository.BookingRepository
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

    override fun findPageByOwnerUserId(
        ownerUserId: Long,
        status: BookingStatus?,
        pageable: Pageable,
    ): Page<Booking> =
        bookingJpaRepository.findPageByOwnerUserId(ownerUserId, status, pageable)

    override fun countBySlotIdAndStatusIn(slotId: Long, statuses: List<BookingStatus>): Long =
        bookingJpaRepository.countBySlotIdAndStatusIn(slotId, statuses)

    override fun findPendingCreatedBefore(before: ZonedDateTime, afterId: Long, limit: Int): List<BookingExpiryCandidate> =
        bookingJpaRepository.findPendingCreatedBefore(before, afterId, limit)

    override fun tryExpire(bookingId: Long): Boolean =
        bookingJpaRepository.tryExpire(bookingId)

    override fun tryConfirm(bookingId: Long, paymentId: Long): Boolean =
        // named argument 강제(6차 재리뷰 p3) — 인접한 동일 타입(Long) 위치 인자 뒤바뀜 방지.
        bookingJpaRepository.tryConfirm(bookingId = bookingId, paymentId = paymentId)

    override fun countConfirmedByOwnerUserIdAndDateRange(ownerUserId: Long, from: ZonedDateTime, to: ZonedDateTime): Long =
        bookingKpiQueryRepository.countConfirmedByOwnerUserIdAndDateRange(ownerUserId, from, to)

    override fun countRefundedByOwnerUserIdAndDateRange(ownerUserId: Long, from: ZonedDateTime, to: ZonedDateTime): Long =
        bookingKpiQueryRepository.countRefundedByOwnerUserIdAndDateRange(ownerUserId, from, to)

    override fun sumSlotCapacityByOwnerUserIdAndDateRange(ownerUserId: Long, from: ZonedDateTime, to: ZonedDateTime): Long =
        bookingKpiQueryRepository.sumSlotCapacityByOwnerUserIdAndDateRange(ownerUserId, from, to)

    override fun findTopFacilityIdsByOwnerUserIdAndDateRange(ownerUserId: Long, from: ZonedDateTime, to: ZonedDateTime, limit: Int): List<String> =
        bookingKpiQueryRepository.findTopFacilityIdsByOwnerUserIdAndDateRange(ownerUserId, from, to, limit)
}
