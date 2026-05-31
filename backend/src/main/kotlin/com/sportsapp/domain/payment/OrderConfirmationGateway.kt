package com.sportsapp.domain.payment

interface OrderConfirmationGateway {
    fun confirm(orderType: OrderType, orderId: Long, paymentId: Long)
}
