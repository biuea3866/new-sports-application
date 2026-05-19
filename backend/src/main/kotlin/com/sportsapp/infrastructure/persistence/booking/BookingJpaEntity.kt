package com.sportsapp.infrastructure.persistence.booking

import com.sportsapp.domain.booking.Booking
import com.sportsapp.domain.booking.BookingStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "bookings")
class BookingJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long,

    @Column(nullable = false)
    val userId: Long,

    @Column(nullable = false)
    val slotId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: BookingStatus,

    @Column(nullable = true)
    val paymentId: Long?,

    @Column(nullable = false)
    val createdAt: ZonedDateTime,
) {
    fun toDomain(): Booking = Booking.reconstruct(
        id = id,
        userId = userId,
        slotId = slotId,
        status = status,
        paymentId = paymentId,
        createdAt = createdAt,
    )

    companion object {
        fun fromDomain(booking: Booking): BookingJpaEntity = BookingJpaEntity(
            id = booking.id,
            userId = booking.userId,
            slotId = booking.slotId,
            status = booking.status,
            paymentId = booking.paymentId,
            createdAt = booking.createdAt,
        )
    }
}
