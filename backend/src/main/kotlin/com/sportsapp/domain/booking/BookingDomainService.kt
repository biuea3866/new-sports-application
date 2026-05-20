package com.sportsapp.domain.booking

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

@Service
class BookingDomainService(
    private val bookingRepository: BookingRepository,
    private val slotRepository: SlotRepository,
) {
    fun createPendingBooking(userId: Long, slotId: Long): Booking {
        slotRepository.findById(slotId)
            ?: throw ResourceNotFoundException("Slot", slotId)
        val booking = Booking.createPending(
            userId = userId,
            slotId = slotId,
        )
        return bookingRepository.save(booking)
    }

    fun confirmBooking(bookingId: Long, paymentId: Long): Booking {
        val booking = bookingRepository.findById(bookingId)
            ?: throw ResourceNotFoundException("Booking", bookingId)
        booking.confirm(paymentId)
        return bookingRepository.save(booking)
    }

    fun cancelBooking(bookingId: Long): Booking {
        val booking = bookingRepository.findById(bookingId)
            ?: throw ResourceNotFoundException("Booking", bookingId)
        booking.cancel()
        return bookingRepository.save(booking)
    }

    fun findMyBookings(userId: Long, status: BookingStatus?, pageable: Pageable): Page<Booking> =
        bookingRepository.findPageByUserId(userId, status, pageable)

    fun getBooking(requesterId: Long, bookingId: Long): Booking {
        val booking = bookingRepository.findById(bookingId)
            ?: throw ResourceNotFoundException("Booking", bookingId)
        if (booking.userId == requesterId) return booking
        if (isFacilityOwner(requesterId, booking.slotId)) return booking
        throw UnauthorizedBookingAccessException(bookingId)
    }

    // TODO: AUTH-05 머지 후 실제 Facility ownerId 조회로 교체
    // 현재는 facilityId 1L 소유자 == userId 1L 로 placeholder 처리
    private fun isFacilityOwner(requesterId: Long, slotId: Long): Boolean {
        val slot = slotRepository.findById(slotId) ?: return false
        return slot.facilityId == "FAC-01" && requesterId == 1L
    }
}
