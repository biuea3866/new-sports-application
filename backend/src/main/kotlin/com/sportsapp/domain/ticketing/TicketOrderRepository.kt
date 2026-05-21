package com.sportsapp.domain.ticketing

interface TicketOrderRepository {
    fun save(ticketOrder: TicketOrder): TicketOrder
    fun findById(id: Long): TicketOrder?
    fun findByUserId(userId: Long): List<TicketOrder>
    fun countConfirmedOrdersByEventId(eventId: Long): Long
}
