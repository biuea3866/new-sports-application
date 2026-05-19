package com.sportsapp.infrastructure.persistence.booking

import com.sportsapp.domain.booking.Booking
import com.sportsapp.domain.booking.BookingRepository
import com.sportsapp.domain.booking.BookingStatus
import org.springframework.stereotype.Repository
import java.time.ZonedDateTime

@Repository
class BookingRepositoryImpl(
    private val bookingJpaRepository: BookingJpaRepository,
) : BookingRepository {

    override fun save(booking: Booking): Booking =
        bookingJpaRepository.save(BookingJpaEntity.fromDomain(booking)).toDomain()

    override fun findById(id: Long): Booking? =
        bookingJpaRepository.findById(id).orElse(null)?.toDomain()

    override fun findByUserIdAndStatus(userId: Long, status: BookingStatus): List<Booking> =
        bookingJpaRepository.findAllByUserIdAndStatus(userId, status).map { it.toDomain() }

    override fun findByUserIdAndStatusAndDateRange(
        userId: Long,
        status: BookingStatus?,
        from: ZonedDateTime?,
        to: ZonedDateTime?,
    ): List<Booking> =
        bookingJpaRepository.findByUserIdAndStatusAndDateRange(userId, status, from, to)
                            .map { it.toDomain() }
}
