package com.sportsapp.infrastructure.ticketing.mysql

import com.sportsapp.domain.ticketing.entity.Seat
import org.springframework.data.jpa.repository.JpaRepository

interface SeatJpaRepository : JpaRepository<Seat, Long> {
    fun findByEventIdAndDeletedAtIsNullOrderBySectionAscRowNoAscSeatNoAsc(eventId: Long): List<Seat>
    fun findByEventIdAndDeletedAtIsNull(eventId: Long): List<Seat>
}
