package com.sportsapp.infrastructure.persistence.booking

import com.sportsapp.domain.booking.Booking
import com.sportsapp.domain.booking.BookingStatus
import org.springframework.data.jpa.repository.JpaRepository

interface BookingJpaRepository : JpaRepository<Booking, Long>, BookingQueryDslRepository {
    fun findAllByUserIdAndStatus(userId: Long, status: BookingStatus): List<Booking>
    fun countBySlotIdAndStatusIn(slotId: Long, statuses: List<BookingStatus>): Long
}
