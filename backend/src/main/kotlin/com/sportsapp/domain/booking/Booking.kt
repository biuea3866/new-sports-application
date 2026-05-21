package com.sportsapp.domain.booking

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
    private val domainEvents: MutableList<DomainEvent> = mutableListOf()

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
        ): Booking = Booking(
            userId = userId,
            slotId = slotId,
            initialStatus = BookingStatus.PENDING,
            initialPaymentId = null,
        )
    }
}
