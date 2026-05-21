package com.sportsapp.presentation.b2b

import com.sportsapp.application.goods.RestoreStockCommand

data class RestoreStockRequest(val quantity: Int) {
    fun toCommand(productId: Long): RestoreStockCommand = RestoreStockCommand(
        productId = productId,
        quantity = quantity,
    )
}
