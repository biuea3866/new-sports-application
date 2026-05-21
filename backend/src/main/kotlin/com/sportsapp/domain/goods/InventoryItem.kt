package com.sportsapp.domain.goods

data class InventoryItem(
    val productId: Long,
    val productName: String,
    val stockQuantity: Int,
    val lowStock: Boolean,
)
