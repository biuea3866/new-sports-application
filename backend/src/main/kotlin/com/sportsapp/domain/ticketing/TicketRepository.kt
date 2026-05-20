package com.sportsapp.domain.ticketing

interface TicketRepository {
    fun save(ticket: Ticket): Ticket
    fun saveAll(tickets: List<Ticket>): List<Ticket>
    fun findByTicketOrderId(ticketOrderId: Long): List<Ticket>
}
