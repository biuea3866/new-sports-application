package com.sportsapp.presentation.goods

import com.sportsapp.application.goods.RestoreMyProductStockCommand
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull

data class RestoreMyProductStockRequest(
    @field:NotNull
    @field:Min(1)
    val quantity: Int,
) {
    fun toCommand(productId: Long) = RestoreMyProductStockCommand(
        productId = productId,
        quantity = quantity,
    )
}
