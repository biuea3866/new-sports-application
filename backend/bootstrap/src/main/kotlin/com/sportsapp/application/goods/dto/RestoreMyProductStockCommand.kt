package com.sportsapp.application.goods.dto

data class RestoreMyProductStockCommand(
    val productId: Long,
    val quantity: Int,
)
