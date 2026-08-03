package com.sportsapp.infrastructure.booking.mysql

import com.sportsapp.domain.booking.dto.BookingExpiryCandidate
import com.sportsapp.domain.booking.entity.Booking
import com.sportsapp.domain.booking.entity.BookingStatus
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

    fun findIdsByOwnerUserId(ownerUserId: Long): List<Long>

    fun findPageByOwnerUserId(
        ownerUserId: Long,
        status: BookingStatus?,
        pageable: Pageable,
    ): Page<Booking>

    fun findPendingCreatedBefore(before: ZonedDateTime, afterId: Long, limit: Int): List<BookingExpiryCandidate>

    fun tryExpire(bookingId: Long): Boolean

    fun tryConfirm(bookingId: Long, paymentId: Long): Boolean
}
