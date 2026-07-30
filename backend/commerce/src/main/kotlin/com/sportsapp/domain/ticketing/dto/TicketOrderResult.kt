package com.sportsapp.domain.ticketing.dto

import com.sportsapp.domain.ticketing.entity.OrderStatus
import com.sportsapp.domain.ticketing.entity.TicketOrder

data class TicketOrderResult(
    val ticketOrderId: Long,
    val status: OrderStatus,
) {
    companion object {
        fun of(ticketOrder: TicketOrder): TicketOrderResult = TicketOrderResult(
            ticketOrderId = ticketOrder.id,
            status = ticketOrder.status,
        )
    }
}
