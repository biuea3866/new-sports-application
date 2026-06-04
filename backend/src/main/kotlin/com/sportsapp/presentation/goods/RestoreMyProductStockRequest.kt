package com.sportsapp.presentation.goods

import com.sportsapp.application.goods.RestoreMyProductStockCommand
import jakarta.validation.constraints.NotNull

data class RestoreMyProductStockRequest(
    @field:NotNull
    val quantity: Int,
) {
    fun toCommand(productId: Long) = RestoreMyProductStockCommand(
        productId = productId,
        quantity = quantity,
    )
}
