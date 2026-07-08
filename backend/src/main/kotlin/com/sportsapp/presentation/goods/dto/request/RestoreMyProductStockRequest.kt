package com.sportsapp.presentation.goods.dto.request

import com.sportsapp.application.goods.dto.RestoreMyProductStockCommand
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
