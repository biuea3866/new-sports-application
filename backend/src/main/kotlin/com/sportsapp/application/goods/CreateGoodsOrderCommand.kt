package com.sportsapp.application.goods

import com.sportsapp.domain.goods.OrderItemInput
import com.sportsapp.domain.payment.PaymentMethod

data class CreateGoodsOrderCommand(
    val userId: Long,
    val idempotencyKey: String,
    val method: PaymentMethod,
    val fromCart: Boolean,
    val items: List<OrderItemInput>,
)
