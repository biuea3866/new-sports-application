package com.sportsapp.application.goods.dto

data class InventoryResult(
    val ownerUserId: Long,
    val activeProductCount: Long,
    val outOfStockProductCount: Long,
)
