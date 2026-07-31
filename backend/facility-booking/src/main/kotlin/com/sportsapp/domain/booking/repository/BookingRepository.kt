package com.sportsapp.domain.booking.repository

import com.sportsapp.domain.booking.entity.Booking
import com.sportsapp.domain.booking.entity.BookingStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.ZonedDateTime

interface BookingRepository {
    fun save(booking: Booking): Booking
    fun findById(id: Long): Booking?
    fun findByUserIdAndStatus(userId: Long, status: BookingStatus): List<Booking>
    fun findByUserIdAndStatusAndDateRange(
        userId: Long,
        status: BookingStatus?,
        from: ZonedDateTime?,
        to: ZonedDateTime?,
    ): List<Booking>
    fun findPageByUserId(
        userId: Long,
        status: BookingStatus?,
        pageable: Pageable,
    ): Page<Booking>
    fun countBySlotIdAndStatusIn(slotId: Long, statuses: List<BookingStatus>): Long

    /**
     * W1-11c 만료 스위퍼가 소비 — PENDING 상태이며 createdAt이 before보다 이른 예약 id를
     * createdAt(id) 오름차순으로 최대 limit건 조회한다(청크 조회).
     */
    fun findPendingCreatedBefore(before: ZonedDateTime, limit: Int): List<Long>
    fun countConfirmedByOwnerUserIdAndDateRange(ownerUserId: Long, from: ZonedDateTime, to: ZonedDateTime): Long
    fun countRefundedByOwnerUserIdAndDateRange(ownerUserId: Long, from: ZonedDateTime, to: ZonedDateTime): Long
    fun sumSlotCapacityByOwnerUserIdAndDateRange(ownerUserId: Long, from: ZonedDateTime, to: ZonedDateTime): Long
    fun findTopFacilityIdsByOwnerUserIdAndDateRange(ownerUserId: Long, from: ZonedDateTime, to: ZonedDateTime, limit: Int): List<String>
}
