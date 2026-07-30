package com.sportsapp.domain.booking.service

import com.sportsapp.domain.booking.dto.BookingDetail
import com.sportsapp.domain.booking.dto.BookingOrderItem
import com.sportsapp.domain.booking.dto.BookingResult
import com.sportsapp.domain.booking.dto.FacilityKpiSummary
import com.sportsapp.domain.booking.entity.Booking
import com.sportsapp.domain.booking.entity.BookingStatus
import com.sportsapp.domain.booking.event.BookingRefundRequestedEvent
import com.sportsapp.domain.booking.event.BookingRequestedEvent
import com.sportsapp.domain.booking.exception.SlotBusyException
import com.sportsapp.domain.booking.exception.SlotFullException
import com.sportsapp.domain.booking.exception.UnauthorizedBookingAccessException
import com.sportsapp.domain.booking.repository.BookingOrderQueryRepository
import com.sportsapp.domain.booking.repository.BookingRepository
import com.sportsapp.domain.booking.repository.SlotRepository
import com.sportsapp.domain.common.DistributedLock
import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.ZonedDateTime
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

private const val TOP_FACILITY_LIMIT = 5
private val LOCK_TTL = Duration.ofSeconds(10)
private val LOCK_WAIT_TIMEOUT = Duration.ofSeconds(5)
private val LOCK_RETRY_INTERVAL = Duration.ofMillis(50)

@Service
class BookingDomainService(
    private val bookingRepository: BookingRepository,
    private val slotRepository: SlotRepository,
    private val distributedLock: DistributedLock,
    private val domainEventPublisher: DomainEventPublisher,
    private val bookingOrderQueryRepository: BookingOrderQueryRepository,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun requestBooking(userId: Long, slotId: Long): BookingResult {
        val lockKey = "booking:slot:$slotId"
        val lockValue = "user:$userId"
        if (!spinLock(lockKey, lockValue)) throw SlotBusyException(slotId)
        registerUnlockOnCompletion(lockKey, lockValue)
        return doBooking(userId, slotId, lockKey, lockValue)
    }

    private fun doBooking(userId: Long, slotId: Long, lockKey: String, lockValue: String): BookingResult {
        try {
            val slot = slotRepository.findForUpdateById(slotId)
                ?: throw ResourceNotFoundException("Slot", slotId)
            slot.requireBookable()
            val activeCount = bookingRepository.countBySlotIdAndStatusIn(
                slotId,
                listOf(BookingStatus.PENDING, BookingStatus.CONFIRMED),
            )
            if (activeCount >= slot.capacity) throw SlotFullException(slotId)
            val booking = Booking.createPending(userId, slotId)
            booking.registerEvent(BookingRequestedEvent(bookingId = booking.id, slotId = slotId, userId = userId))
            val saved = bookingRepository.save(booking)
            domainEventPublisher.publishAll(saved.pullDomainEvents())
            return BookingResult(
                bookingId = saved.id,
                slotId = saved.slotId,
                userId = saved.userId,
                status = saved.status,
            )
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
        if (booking.status == BookingStatus.CONFIRMED) return booking
        booking.confirm(paymentId)
        val saved = bookingRepository.save(booking)
        domainEventPublisher.publishAll(saved.pullDomainEvents())
        return saved
    }

    fun cancelPending(bookingId: Long) {
        val booking = bookingRepository.findById(bookingId)
            ?: throw ResourceNotFoundException("Booking", bookingId)
        if (booking.status == BookingStatus.CANCELLED) return
        booking.cancel()
        bookingRepository.save(booking)
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

    /**
     * order 통합 조회(BE-08)가 소비하는 사용자별 주문(라벨 title 포함) 조회.
     * orderType=BOOKING 매핑은 order 파사드가 담당한다.
     */
    fun findOrderHistory(userId: Long): List<BookingOrderItem> =
        bookingOrderQueryRepository.findByUserId(userId)

    /**
     * 시설 KPI를 집계합니다.
     *
     * **노쇼율 산정 기준**: 현재 스키마에 NO_SHOW 전용 상태가 없으므로
     * REFUNDED(결제 완료 후 환불된 예약)를 노쇼 proxy로 사용합니다.
     * 향후 NO_SHOW 상태가 추가되면 [countRefundedByOwnerUserIdAndDateRange] 호출을 대체해야 합니다.
     */
    fun aggregateFacilityKpi(ownerUserId: Long, from: ZonedDateTime, to: ZonedDateTime): FacilityKpiSummary {
        val confirmedCount = bookingRepository.countConfirmedByOwnerUserIdAndDateRange(ownerUserId, from, to)
        val totalCapacity = bookingRepository.sumSlotCapacityByOwnerUserIdAndDateRange(ownerUserId, from, to)
        val noShowCount = bookingRepository.countRefundedByOwnerUserIdAndDateRange(ownerUserId, from, to)
        val topFacilityIds = bookingRepository.findTopFacilityIdsByOwnerUserIdAndDateRange(ownerUserId, from, to, TOP_FACILITY_LIMIT)

        val utilizationRate = if (totalCapacity > 0) {
            BigDecimal(confirmedCount).multiply(BigDecimal(100))
                .divide(BigDecimal(totalCapacity), 2, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO

        val totalBookings = confirmedCount + noShowCount
        val noShowRate = if (totalBookings > 0) {
            BigDecimal(noShowCount).multiply(BigDecimal(100))
                .divide(BigDecimal(totalBookings), 2, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO

        return FacilityKpiSummary(
            utilizationRate = utilizationRate,
            noShowRate = noShowRate,
            topFacilityIds = topFacilityIds,
        )
    }


    fun getBooking(requesterId: Long, bookingId: Long): Booking {
        val booking = bookingRepository.findById(bookingId)
            ?: throw ResourceNotFoundException("Booking", bookingId)
        if (booking.userId == requesterId) return booking
        // TODO(AUTH-05): Facility.ownerId 조회로 FACILITY_OWNER 권한 분기 추가
        throw UnauthorizedBookingAccessException(bookingId)
    }

    /**
     * 단건 조회(GET /bookings/{id})가 소비하는 booking + slot 조인 상세.
     * Slot도 booking 컨텍스트 자기 aggregate이므로 조인해도 도메인 교차가 아니다.
     */
    fun getBookingDetail(requesterId: Long, bookingId: Long): BookingDetail {
        val booking = getBooking(requesterId, bookingId)
        val slot = slotRepository.findById(booking.slotId)
        return BookingDetail.of(booking, slot)
    }

    fun refundBooking(bookingId: Long, callerUserId: Long, refundAmount: BigDecimal, reason: String): Booking {
        val booking = bookingRepository.findById(bookingId)
            ?: throw ResourceNotFoundException("Booking", bookingId)
        booking.requireOwnedBy(callerUserId)
        val paymentId = booking.requireHasPayment()
        booking.refund()
        val saved = bookingRepository.save(booking)
        domainEventPublisher.publishAll(
            listOf(
                BookingRefundRequestedEvent(
                    bookingId = saved.id,
                    paymentId = paymentId,
                    refundAmount = refundAmount,
                    reason = reason,
                ),
            ),
        )
        return saved
    }

}
