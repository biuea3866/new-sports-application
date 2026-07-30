package com.sportsapp.domain.ticketing.repository

import com.sportsapp.domain.ticketing.entity.Ticket

interface TicketRepository {
    fun save(ticket: Ticket): Ticket
    fun saveAll(tickets: List<Ticket>): List<Ticket>
    fun findByTicketOrderId(ticketOrderId: Long): List<Ticket>
}
