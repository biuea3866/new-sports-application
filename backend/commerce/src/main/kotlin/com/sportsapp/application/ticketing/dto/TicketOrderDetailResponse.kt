package com.sportsapp.application.ticketing.dto

import com.sportsapp.domain.ticketing.dto.TicketOrderDetail
import com.sportsapp.domain.ticketing.entity.OrderStatus
import java.time.ZonedDateTime

data class TicketOrderDetailResponse(
    val ticketOrderId: Long,
    val status: OrderStatus,
    val eventId: Long,
    val eventTitle: String,
    val paymentId: Long?,
    val createdAt: ZonedDateTime,
) {
    companion object {
        fun of(detail: TicketOrderDetail): TicketOrderDetailResponse = TicketOrderDetailResponse(
            ticketOrderId = detail.ticketOrderId,
            status = detail.status,
            eventId = detail.eventId,
            eventTitle = detail.eventTitle,
            paymentId = detail.paymentId,
            createdAt = detail.createdAt,
        )
    }
}
