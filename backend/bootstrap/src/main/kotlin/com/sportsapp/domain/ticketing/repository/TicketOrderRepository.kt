package com.sportsapp.domain.ticketing.repository

import com.sportsapp.domain.ticketing.entity.TicketOrder

interface TicketOrderRepository {
    fun save(ticketOrder: TicketOrder): TicketOrder
    fun findById(id: Long): TicketOrder?
    fun findByUserId(userId: Long): List<TicketOrder>
}
