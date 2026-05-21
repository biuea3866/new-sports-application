package com.sportsapp.application.ticketing

import com.sportsapp.domain.ticketing.OrderStatus
import com.sportsapp.domain.ticketing.TicketOrder

data class TicketOrderResponse(
    val ticketOrderId: Long,
    val status: OrderStatus,
) {
    companion object {
        fun of(ticketOrder: TicketOrder): TicketOrderResponse = TicketOrderResponse(
            ticketOrderId = ticketOrder.id,
            status = ticketOrder.status,
        )
    }
}
