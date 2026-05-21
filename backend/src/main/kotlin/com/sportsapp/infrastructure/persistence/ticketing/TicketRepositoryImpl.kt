package com.sportsapp.infrastructure.persistence.ticketing

import com.sportsapp.domain.ticketing.Ticket
import com.sportsapp.domain.ticketing.TicketRepository
import org.springframework.stereotype.Component

@Component
class TicketRepositoryImpl(
    private val ticketJpaRepository: TicketJpaRepository,
) : TicketRepository {

    override fun save(ticket: Ticket): Ticket =
        ticketJpaRepository.save(ticket)

    override fun saveAll(tickets: List<Ticket>): List<Ticket> =
        ticketJpaRepository.saveAll(tickets)

    override fun findByTicketOrderId(ticketOrderId: Long): List<Ticket> =
        ticketJpaRepository.findByTicketOrderIdAndDeletedAtIsNull(ticketOrderId)
}
