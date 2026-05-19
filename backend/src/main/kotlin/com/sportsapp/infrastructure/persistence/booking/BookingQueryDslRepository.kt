package com.sportsapp.infrastructure.persistence.booking

import java.time.ZonedDateTime

interface BookingQueryDslRepository {
    fun findByUserIdAndStatusAndDateRange(
        userId: Long,
        status: com.sportsapp.domain.booking.BookingStatus?,
        from: ZonedDateTime?,
        to: ZonedDateTime?,
    ): List<BookingJpaEntity>
}
