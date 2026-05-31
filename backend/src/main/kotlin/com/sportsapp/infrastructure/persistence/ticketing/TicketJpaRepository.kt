package com.sportsapp.infrastructure.persistence.ticketing

import com.sportsapp.domain.ticketing.Ticket
import com.sportsapp.domain.ticketing.TicketStatus
import org.springframework.data.jpa.repository.JpaRepository

interface TicketJpaRepository : JpaRepository<Ticket, Long> {
    fun findByTicketOrderIdAndDeletedAtIsNull(ticketOrderId: Long): List<Ticket>
    fun findByTicketOrderIdAndStatusAndDeletedAtIsNull(ticketOrderId: Long, status: TicketStatus): List<Ticket>
}
