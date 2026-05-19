package com.sportsapp.domain.booking

import java.time.ZonedDateTime

class Booking private constructor(
    val id: Long,
    val userId: Long,
    val slotId: Long,
    var status: BookingStatus,
    var paymentId: Long?,
    val createdAt: ZonedDateTime,
) {
    fun confirm(paymentId: Long) {
        if (status == BookingStatus.CONFIRMED) {
            return
        }
        if (!status.canTransitTo(BookingStatus.CONFIRMED)) {
            throw InvalidBookingStateException(status, BookingStatus.CONFIRMED)
        }
        this.status = BookingStatus.CONFIRMED
        this.paymentId = paymentId
    }

    fun cancel() {
        if (!status.canTransitTo(BookingStatus.CANCELLED)) {
            throw InvalidBookingStateException(status, BookingStatus.CANCELLED)
        }
        this.status = BookingStatus.CANCELLED
    }

    fun expire() {
        if (!status.canTransitTo(BookingStatus.EXPIRED)) {
            throw InvalidBookingStateException(status, BookingStatus.EXPIRED)
        }
        this.status = BookingStatus.EXPIRED
    }

    companion object {
        fun createPending(
            userId: Long,
            slotId: Long,
            createdAt: ZonedDateTime,
        ): Booking = Booking(
            id = 0L,
            userId = userId,
            slotId = slotId,
            status = BookingStatus.PENDING,
            paymentId = null,
            createdAt = createdAt,
        )

        fun reconstruct(
            id: Long,
            userId: Long,
            slotId: Long,
            status: BookingStatus,
            paymentId: Long?,
            createdAt: ZonedDateTime,
        ): Booking = Booking(
            id = id,
            userId = userId,
            slotId = slotId,
            status = status,
            paymentId = paymentId,
            createdAt = createdAt,
        )
    }
}
