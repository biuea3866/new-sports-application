package com.sportsapp.infrastructure.persistence.booking

import com.sportsapp.domain.booking.Booking
import com.sportsapp.domain.booking.BookingStatus
import com.sportsapp.domain.booking.FacilityBookingStats
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.ZonedDateTime

interface BookingQueryDslRepository {
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
