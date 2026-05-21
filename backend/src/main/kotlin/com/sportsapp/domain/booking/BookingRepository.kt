package com.sportsapp.domain.booking

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
    fun findNoShowsByOwnerAndPeriod(
        ownerUserId: Long,
        facilityId: String?,
        from: ZonedDateTime,
        to: ZonedDateTime,
        pageable: Pageable,
    ): Page<Booking>

    fun aggregateStatsByFacilityIds(
        facilityIds: List<String>,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): List<FacilityBookingStats>
}
