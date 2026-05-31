package com.sportsapp.domain.ticketing

data class ConfirmOrderResult(
    val ticketOrderId: Long,
    val status: OrderStatus,
)
