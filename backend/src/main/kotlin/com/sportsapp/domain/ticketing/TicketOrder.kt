package com.sportsapp.domain.ticketing

import com.sportsapp.domain.common.JpaAuditingBase
import io.hypersistence.utils.hibernate.type.json.JsonStringType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Type

@Entity
@Table(name = "ticket_orders")
class TicketOrder(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: OrderStatus,

    @Column(name = "payment_id")
    var paymentId: Long?,

    @Column(name = "locked_event_id", nullable = false)
    val lockedEventId: Long,

    @Type(JsonStringType::class)
    @Column(name = "locked_seat_ids", columnDefinition = "TEXT", nullable = false)
    val lockedSeatIds: List<Long>,
) : JpaAuditingBase() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0

    fun confirm(paymentId: Long, seatIds: List<Long>): List<Ticket> {
        status.requireCanTransitTo(OrderStatus.CONFIRMED)
        status = OrderStatus.CONFIRMED
        this.paymentId = paymentId
        return seatIds.map { seatId -> Ticket.issue(ticketOrderId = id, seatId = seatId) }
    }

    fun cancel() {
        status.requireCanTransitTo(OrderStatus.CANCELLED)
        status = OrderStatus.CANCELLED
    }

    fun requireOwnedBy(userId: Long) {
        if (this.userId != userId) {
            throw com.sportsapp.domain.common.exceptions.BusinessRuleViolationException(
                "TicketOrder $id is not owned by user $userId"
            )
        }
    }

    companion object {
        fun create(
            userId: Long,
            lockedEventId: Long,
            lockedSeatIds: List<Long>,
        ): TicketOrder = TicketOrder(
            userId = userId,
            status = OrderStatus.PENDING,
            paymentId = null,
            lockedEventId = lockedEventId,
            lockedSeatIds = lockedSeatIds,
        )
    }
}
