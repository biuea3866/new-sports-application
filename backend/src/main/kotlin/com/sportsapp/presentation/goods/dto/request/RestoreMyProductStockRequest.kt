package com.sportsapp.presentation.goods.dto.request

import com.sportsapp.application.goods.dto.RestoreMyProductStockCommand
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
