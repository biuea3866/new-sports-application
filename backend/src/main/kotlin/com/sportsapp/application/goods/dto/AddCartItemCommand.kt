package com.sportsapp.application.goods.dto

data class AddCartItemCommand(
    val userId: Long,
    val productId: Long,
    val quantity: Int,
)
