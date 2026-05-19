package com.sportsapp.infrastructure.persistence.booking

import com.sportsapp.domain.booking.BookingStatus
import org.springframework.data.jpa.repository.JpaRepository

interface BookingJpaRepository : JpaRepository<BookingJpaEntity, Long>, BookingQueryDslRepository {
    fun findAllByUserIdAndStatus(userId: Long, status: BookingStatus): List<BookingJpaEntity>
}
