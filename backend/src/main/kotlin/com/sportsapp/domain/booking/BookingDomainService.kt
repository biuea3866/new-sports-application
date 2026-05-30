package com.sportsapp.domain.booking

import com.sportsapp.domain.common.DistributedLock
import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import java.math.BigDecimal
import java.time.Duration
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

private val LOCK_TTL = Duration.ofSeconds(10)
private val LOCK_WAIT_TIMEOUT = Duration.ofSeconds(5)
private val LOCK_RETRY_INTERVAL = Duration.ofMillis(50)

@Service
class BookingDomainService(
    private val bookingRepository: BookingRepository,
    private val slotRepository: SlotRepository,
    private val distributedLock: DistributedLock,
    private val domainEventPublisher: DomainEventPublisher,
    private val paymentRefundGateway: PaymentRefundGateway,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun requestBooking(userId: Long, slotId: Long): Booking {
        val lockKey = "booking:slot:$slotId"
        val lockValue = "user:$userId"
        if (!spinLock(lockKey, lockValue)) throw SlotBusyException(slotId)
        registerUnlockOnCompletion(lockKey, lockValue)
        return doBooking(userId, slotId, lockKey, lockValue)
    }

    private fun doBooking(userId: Long, slotId: Long, lockKey: String, lockValue: String): Booking {
        try {
            val slot = slotRepository.findById(slotId)
                ?: throw ResourceNotFoundException("Slot", slotId)
            val activeCount = bookingRepository.countBySlotIdAndStatusIn(
                slotId,
                listOf(BookingStatus.PENDING, BookingStatus.CONFIRMED),
            )
            if (activeCount >= slot.capacity) throw SlotFullException(slotId)
            val booking = bookingRepository.save(Booking.createPending(userId, slotId))
            booking.registerEvent(BookingRequestedEvent(bookingId = booking.id, slotId = slotId, userId = userId))
            domainEventPublisher.publishAll(booking.pullDomainEvents())
            return booking
        } finally {
            if (!TransactionSynchronizationManager.isActualTransactionActive()) {
                distributedLock.unlock(lockKey, lockValue)
            }
        }
    }

    private fun registerUnlockOnCompletion(lockKey: String, lockValue: String) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) return
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCompletion(status: Int) {
                distributedLock.unlock(lockKey, lockValue)
            }
        })
    }

    private fun spinLock(key: String, value: String): Boolean {
        val deadline = System.currentTimeMillis() + LOCK_WAIT_TIMEOUT.toMillis()
        while (System.currentTimeMillis() < deadline) {
            if (distributedLock.tryLock(key, value, LOCK_TTL)) return true
            Thread.sleep(LOCK_RETRY_INTERVAL.toMillis())
        }
        return false
    }

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

    fun cancel(bookingId: Long, cancelledByUserId: Long, reason: String?): Booking {
        val booking = bookingRepository.findById(bookingId)
            ?: throw ResourceNotFoundException("Booking", bookingId)
        booking.cancel(cancelledByUserId, reason)
        val saved = bookingRepository.save(booking)
        domainEventPublisher.publishAll(saved.pullDomainEvents())
        return saved
    }

    fun findMyBookings(userId: Long, status: BookingStatus?, pageable: Pageable): Page<Booking> =
        bookingRepository.findPageByUserId(userId, status, pageable)


    fun getBooking(requesterId: Long, bookingId: Long): Booking {
        val booking = bookingRepository.findById(bookingId)
            ?: throw ResourceNotFoundException("Booking", bookingId)
        if (booking.userId == requesterId) return booking
        // TODO(AUTH-05): Facility.ownerId 조회로 FACILITY_OWNER 권한 분기 추가
        throw UnauthorizedBookingAccessException(bookingId)
    }

    fun refundBooking(bookingId: Long, callerUserId: Long, refundAmount: BigDecimal, reason: String): Booking {
        val booking = bookingRepository.findById(bookingId)
            ?: throw ResourceNotFoundException("Booking", bookingId)
        booking.requireOwnedBy(callerUserId)
        val paymentId = booking.requireHasPayment()
        paymentRefundGateway.requestRefund(paymentId.toString(), refundAmount, reason)
        booking.refund()
        return bookingRepository.save(booking)
    }

}
