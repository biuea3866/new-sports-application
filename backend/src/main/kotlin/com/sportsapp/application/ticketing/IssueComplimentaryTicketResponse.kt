package com.sportsapp.application.ticketing

import com.sportsapp.domain.ticketing.Ticket
import com.sportsapp.domain.ticketing.TicketStatus

data class IssueComplimentaryTicketResponse(
    val ticketId: Long,
    val seatId: Long,
    val status: TicketStatus,
    val code: String,
) {
    companion object {
        fun of(ticket: Ticket): IssueComplimentaryTicketResponse = IssueComplimentaryTicketResponse(
            ticketId = ticket.id,
            seatId = ticket.seatId,
            status = ticket.status,
            code = ticket.code,
        )
    }
}
