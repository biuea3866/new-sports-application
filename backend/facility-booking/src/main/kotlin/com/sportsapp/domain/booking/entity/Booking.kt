package com.sportsapp.domain.booking.entity

import com.sportsapp.domain.booking.event.BookingCancelledEvent
import com.sportsapp.domain.booking.event.BookingEvent
import com.sportsapp.domain.booking.exception.InvalidBookingStateException
import com.sportsapp.domain.booking.exception.RefundBookingException
import com.sportsapp.domain.booking.exception.RefundPolicyViolationException
import com.sportsapp.domain.booking.exception.UnauthorizedBookingAccessException
import com.sportsapp.domain.common.DomainEvent
import com.sportsapp.domain.common.JpaAuditingBase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Transient

@Entity
@Table(name = "bookings")
class Booking(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "slot_id", nullable = false)
    val slotId: Long,

    initialStatus: BookingStatus,
    initialPaymentId: Long?,
) : JpaAuditingBase() {

    @Transient
    private var _domainEvents: MutableList<DomainEvent>? = null

    private val domainEvents: MutableList<DomainEvent>
        get() = _domainEvents ?: mutableListOf<DomainEvent>().also { _domainEvents = it }

    fun pullDomainEvents(): List<DomainEvent> {
        val events = domainEvents.toList()
        domainEvents.clear()
        return events
    }

    internal fun registerEvent(event: DomainEvent) {
        domainEvents.add(event)
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: BookingStatus = initialStatus
        private set

    @Column(name = "payment_id", nullable = true)
    var paymentId: Long? = initialPaymentId
        private set

    /**
     * **CAS가 유일한 프로덕션 전이 경로다.** 실제 확정은
     * [com.sportsapp.domain.booking.repository.BookingRepository.tryConfirm](조건부 UPDATE,
     * WHERE status='PENDING')이 수행하고, 이 메서드는 find→mutate→save 경로로 호출되지 않는다
     * (`BookingDomainService.confirmBooking` 참고 — lost update 방지를 위해 dirty-checking
     * UPDATE를 의도적으로 배제했다). `BookingStatus.canTransitTo`의 상태 머신 정의가 이
     * 메서드와 tryConfirm의 SQL 조건(`WHERE status=PENDING`) 두 곳에 이중화돼 있으므로,
     * 상태 전이 규칙을 바꿀 때는 반드시 두 곳을 함께 갱신할 것 — SQL만 고치고 이 메서드를
     * 방치하면 드리프트가 생긴다.
     */
    fun confirm(paymentId: Long) {
        if (status == BookingStatus.CONFIRMED) {
            return
        }
        if (!status.canTransitTo(BookingStatus.CONFIRMED)) {
            throw InvalidBookingStateException(status, BookingStatus.CONFIRMED)
        }
        this.status = BookingStatus.CONFIRMED
        this.paymentId = paymentId
        registerEvent(
            BookingEvent.Confirmed(bookingId = id, paymentId = paymentId, recipientUserId = userId)
        )
    }

    fun cancel() {
        requireCancellable()
        this.status = BookingStatus.CANCELLED
    }

    fun cancel(cancelledByUserId: Long, reason: String?) {
        requireOwnedBy(cancelledByUserId)
        requireCancellable()
        this.status = BookingStatus.CANCELLED
        registerEvent(BookingCancelledEvent(bookingId = id, cancelledByUserId = cancelledByUserId, reason = reason))
    }

    fun requireCancellable() {
        if (!status.canTransitTo(BookingStatus.CANCELLED)) {
            throw InvalidBookingStateException(status, BookingStatus.CANCELLED)
        }
    }

    fun requireOwnedBy(requestUserId: Long) {
        if (userId != requestUserId) {
            throw UnauthorizedBookingAccessException(id)
        }
    }

    fun refund() {
        if (!status.canTransitTo(BookingStatus.REFUNDED)) {
            throw RefundPolicyViolationException(id, status)
        }
        this.status = BookingStatus.REFUNDED
    }

    fun requireHasPayment(): Long {
        return paymentId ?: throw RefundBookingException(id, "결제 정보가 없는 예약은 환불할 수 없습니다.")
    }

    /**
     * **CAS가 유일한 프로덕션 전이 경로다.** 실제 만료는
     * [com.sportsapp.domain.booking.repository.BookingRepository.tryExpire](조건부 UPDATE,
     * WHERE status='PENDING')가 수행하고, 이 메서드는 만료 스위퍼(`BookingDomainService.expireBookings`)에서
     * find→mutate→save 경로로 호출되지 않는다 — 청크 트랜잭션 스냅샷과 무관하게 최신 커밋본을
     * 평가해야 다른 트랜잭션이 CONFIRMED로 전이시킨 예약을 덮어쓰지 않는다. `confirm()`과
     * 동일하게 상태 머신 정의가 이 메서드와 tryExpire의 SQL 조건 두 곳에 이중화돼 있다.
     */
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
        ): Booking = Booking(
            userId = userId,
            slotId = slotId,
            initialStatus = BookingStatus.PENDING,
            initialPaymentId = null,
        )
    }
}
