package com.sportsapp.application.goods

data class AddCartItemCommand(
    val userId: Long,
    val productId: Long,
    val quantity: Int,
)
