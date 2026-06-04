package com.sportsapp.infrastructure.ticketing.mysql

import com.sportsapp.domain.ticketing.entity.Ticket
import com.sportsapp.domain.ticketing.entity.TicketStatus
import org.springframework.data.jpa.repository.JpaRepository

interface TicketJpaRepository : JpaRepository<Ticket, Long> {
    fun findByTicketOrder_IdAndDeletedAtIsNull(ticketOrderId: Long): List<Ticket>
    fun findByTicketOrder_IdAndStatusAndDeletedAtIsNull(ticketOrderId: Long, status: TicketStatus): List<Ticket>
}
