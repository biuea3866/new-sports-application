package com.sportsapp.application.goods.dto

import com.sportsapp.domain.goods.vo.OrderItemInput
import com.sportsapp.domain.payment.vo.PaymentMethod

data class CreateGoodsOrderCommand(
    val userId: Long,
    val idempotencyKey: String,
    val method: PaymentMethod,
    val fromCart: Boolean,
    val items: List<OrderItemInput>,
)
