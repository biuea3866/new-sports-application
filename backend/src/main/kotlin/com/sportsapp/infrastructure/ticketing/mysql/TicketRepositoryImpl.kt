package com.sportsapp.infrastructure.ticketing.mysql

import com.sportsapp.domain.ticketing.entity.Ticket
import com.sportsapp.domain.ticketing.entity.TicketStatus
import com.sportsapp.domain.ticketing.repository.TicketRepository
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
        ticketJpaRepository.findByTicketOrder_IdAndStatusAndDeletedAtIsNull(ticketOrderId, com.sportsapp.domain.ticketing.entity.TicketStatus.ISSUED)
}
