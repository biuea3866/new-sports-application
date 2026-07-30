package com.sportsapp.application.goods.dto

data class UpdateCartItemCommand(
    val userId: Long,
    val itemId: Long,
    val quantity: Int,
)
