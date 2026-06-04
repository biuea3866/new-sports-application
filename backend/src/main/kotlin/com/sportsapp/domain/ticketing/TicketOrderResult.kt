package com.sportsapp.domain.ticketing

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
