package com.sportsapp.domain.payment.gateway

import com.sportsapp.domain.payment.vo.OrderType

interface OrderConfirmationGateway {
    fun confirm(orderType: OrderType, orderId: Long, paymentId: Long)
    fun cancel(orderType: OrderType, orderId: Long, paymentId: Long)
}
