package com.sportsapp.domain.payment

interface OrderConfirmationGateway {
    fun confirm(orderType: OrderType, orderId: Long, paymentId: Long)
    fun cancel(orderType: OrderType, orderId: Long, paymentId: Long)
}
