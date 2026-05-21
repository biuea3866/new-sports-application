package com.sportsapp.presentation.ticketing

import com.sportsapp.domain.payment.PaymentMethod

data class PurchaseTicketOrderRequest(
    val lockId: String,
    val method: PaymentMethod,
    val currency: String,
)
