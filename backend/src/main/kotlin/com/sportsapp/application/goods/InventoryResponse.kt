package com.sportsapp.application.goods

data class InventoryResponse(
    val ownerUserId: Long,
    val activeProductCount: Long,
    val outOfStockProductCount: Long,
)
