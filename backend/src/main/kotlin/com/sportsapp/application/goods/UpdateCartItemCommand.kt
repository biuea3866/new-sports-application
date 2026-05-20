package com.sportsapp.application.goods

data class UpdateCartItemCommand(
    val userId: Long,
    val itemId: Long,
    val quantity: Int,
)
