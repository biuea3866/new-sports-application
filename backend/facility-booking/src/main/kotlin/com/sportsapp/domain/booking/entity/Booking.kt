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
import java.math.BigDecimal

@Entity
@Table(name = "bookings")
class Booking(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "slot_id", nullable = false)
    val slotId: Long,

    initialStatus: BookingStatus,
    initialPaymentId: Long?,

    /**
     * 예약 결제 금액 — order 통합조회(BE-08 후속, order-amount 결함)가 소비하는 booking 자기
     * 데이터다. payment 테이블을 역참조하지 않고 booking 생성 시점에 자기 컬럼으로 저장한다
     * (V65 마이그레이션). 과거(이 컬럼 도입 전) 예약은 저장 이력이 없어 `null` — 무료(0)와
     * 미확정(null)을 구분해야 하므로 0으로 방어하지 않는다.
     */
    initialAmount: BigDecimal?,
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

    @Column(name = "amount", nullable = true, precision = 15, scale = 2)
    val amount: BigDecimal? = initialAmount

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
            BookingEvent.Confirmed(
                bookingId = id,
                paymentId = paymentId,
                recipientUserId = userId,
                // Entity는 순수해서(Repository 미주입) 슬롯 소유주를 알 수 없다. 소유주를 채우는
                // 운영 경로는 CAS 확정(BookingDomainService#confirmBooking)이며, 이 메서드는
                // 현재 운영 호출부가 없다. 값을 넘겨받으면 시간·소유주 조회 책임이 Entity로
                // 새어 들어오므로 명시적으로 비운다.
                facilityOwnerUserId = null,
            )
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
        /** amount 미지정 호출 — 기존 호출부·시나리오(결제 금액을 다루지 않는 경로)를 그대로 보존한다. */
        fun createPending(
            userId: Long,
            slotId: Long,
        ): Booking = createPending(userId = userId, slotId = slotId, amount = null)

        fun createPending(
            userId: Long,
            slotId: Long,
            amount: BigDecimal?,
        ): Booking = Booking(
            userId = userId,
            slotId = slotId,
            initialStatus = BookingStatus.PENDING,
            initialPaymentId = null,
            initialAmount = amount,
        )
    }
}
