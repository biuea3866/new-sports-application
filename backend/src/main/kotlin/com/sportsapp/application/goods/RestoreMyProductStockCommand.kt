package com.sportsapp.application.goods

data class RestoreMyProductStockCommand(
    val productId: Long,
    val quantity: Int,
)
