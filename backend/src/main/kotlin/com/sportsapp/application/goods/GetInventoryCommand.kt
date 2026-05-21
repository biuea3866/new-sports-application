package com.sportsapp.application.goods

data class GetInventoryCommand(
    val operatorUserId: Long,
    val lowStockOnly: Boolean,
)
