package com.sportsapp.infrastructure.persistence.booking

import com.sportsapp.domain.booking.Booking
import com.sportsapp.domain.booking.BookingRepository
import com.sportsapp.domain.booking.BookingStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import java.time.ZonedDateTime

@Repository
class BookingRepositoryImpl(
    private val bookingJpaRepository: BookingJpaRepository,
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
}
