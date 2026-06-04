package com.sportsapp.application.ticketing.dto

import com.sportsapp.domain.ticketing.entity.OrderStatus
import com.sportsapp.domain.ticketing.entity.TicketOrder

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
