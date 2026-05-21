package com.sportsapp.application.goods

import com.sportsapp.domain.goods.InventoryItem

data class InventoryItemResponse(
    val productId: Long,
    val productName: String,
    val stockQuantity: Int,
    val lowStock: Boolean,
) {
    companion object {
        fun of(item: InventoryItem): InventoryItemResponse = InventoryItemResponse(
            productId = item.productId,
            productName = item.productName,
            stockQuantity = item.stockQuantity,
            lowStock = item.lowStock,
        )
    }
}

data class InventoryResponse(
    val items: List<InventoryItemResponse>,
) {
    companion object {
        fun of(items: List<InventoryItem>): InventoryResponse =
            InventoryResponse(items = items.map { InventoryItemResponse.of(it) })
    }
}
