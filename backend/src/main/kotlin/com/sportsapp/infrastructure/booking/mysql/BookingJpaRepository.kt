package com.sportsapp.infrastructure.booking.mysql

import com.sportsapp.domain.booking.entity.Booking
import com.sportsapp.domain.booking.entity.BookingStatus
import org.springframework.data.jpa.repository.JpaRepository

interface BookingJpaRepository : JpaRepository<Booking, Long>, BookingQueryDslRepository {
    fun findAllByUserIdAndStatus(userId: Long, status: BookingStatus): List<Booking>
    fun countBySlotIdAndStatusIn(slotId: Long, statuses: List<BookingStatus>): Long
}
