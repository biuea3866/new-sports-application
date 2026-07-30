package com.sportsapp.presentation.ticketing.dto.request

import com.sportsapp.domain.payment.vo.PaymentMethod

data class PurchaseTicketOrderRequest(
    val lockId: String,
    val method: PaymentMethod,
    val currency: String,
)
