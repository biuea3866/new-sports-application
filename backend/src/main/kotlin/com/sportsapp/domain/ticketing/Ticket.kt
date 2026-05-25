package com.sportsapp.domain.ticketing

import com.sportsapp.domain.common.JpaAuditingBase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "tickets")
class Ticket(
    @Column(name = "ticket_order_id", nullable = true)
    val ticketOrderId: Long?,

    @Column(name = "seat_id", nullable = false)
    val seatId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: TicketStatus,

    @Column(name = "code", nullable = false, unique = true, length = 64)
    val code: String,
) : JpaAuditingBase() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0

    val isComplimentary: Boolean get() = ticketOrderId == null

    fun revoke() {
        status.requireCanTransitTo(TicketStatus.REVOKED)
        status = TicketStatus.REVOKED
    }

    companion object {
        fun issue(ticketOrderId: Long, seatId: Long): Ticket = Ticket(
            ticketOrderId = ticketOrderId,
            seatId = seatId,
            status = TicketStatus.ISSUED,
            code = generateCode(),
        )

        fun issueComplimentary(seatId: Long): Ticket = Ticket(
            ticketOrderId = null,
            seatId = seatId,
            status = TicketStatus.ISSUED,
            code = generateCode(),
        )

        private fun generateCode(): String =
            UUID.randomUUID().toString().replace("-", "") +
                UUID.randomUUID().toString().replace("-", "")
    }
}
