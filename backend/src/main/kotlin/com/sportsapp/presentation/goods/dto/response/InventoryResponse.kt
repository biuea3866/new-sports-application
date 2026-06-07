package com.sportsapp.presentation.goods.dto.response

import com.sportsapp.application.goods.dto.InventoryResult

data class InventoryResponse(
    val ownerUserId: Long,
    val activeProductCount: Long,
    val outOfStockProductCount: Long,
) {
    companion object {
        fun of(result: InventoryResult) = InventoryResponse(
            ownerUserId = result.ownerUserId,
            activeProductCount = result.activeProductCount,
            outOfStockProductCount = result.outOfStockProductCount,
        )
    }
}
