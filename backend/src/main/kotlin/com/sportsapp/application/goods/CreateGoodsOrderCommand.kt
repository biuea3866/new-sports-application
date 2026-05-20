package com.sportsapp.application.goods

import com.sportsapp.domain.goods.OrderItemInput

data class CreateGoodsOrderCommand(
    val userId: Long,
    val items: List<OrderItemInput>,
)
