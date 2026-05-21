package com.sportsapp.application.goods

data class RestoreStockCommand(
    val productId: Long,
    val quantity: Int,
)
