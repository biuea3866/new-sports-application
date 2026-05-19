package com.sportsapp.domain.booking

import org.springframework.stereotype.Service
import java.time.ZonedDateTime

@Service
class BookingDomainService(
    private val bookingRepository: BookingRepository,
    private val slotRepository: SlotRepository,
) {
    fun createPendingBooking(userId: Long, slotId: Long): Booking {
        slotRepository.findById(slotId)
            ?: throw com.sportsapp.domain.common.exceptions.ResourceNotFoundException("Slot", slotId)
        val booking = Booking.createPending(
            userId = userId,
            slotId = slotId,
            createdAt = ZonedDateTime.now(),
        )
        return bookingRepository.save(booking)
    }

    fun confirmBooking(bookingId: Long, paymentId: Long): Booking {
        val booking = bookingRepository.findById(bookingId)
            ?: throw com.sportsapp.domain.common.exceptions.ResourceNotFoundException("Booking", bookingId)
        booking.confirm(paymentId)
        return bookingRepository.save(booking)
    }

    fun cancelBooking(bookingId: Long): Booking {
        val booking = bookingRepository.findById(bookingId)
            ?: throw com.sportsapp.domain.common.exceptions.ResourceNotFoundException("Booking", bookingId)
        booking.cancel()
        return bookingRepository.save(booking)
    }
}
