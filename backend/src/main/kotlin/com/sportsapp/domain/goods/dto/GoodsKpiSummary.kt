package com.sportsapp.domain.goods.dto

import java.math.BigDecimal

data class GoodsKpiSummary(
    val dailyRevenueTotal: BigDecimal,
    val inventoryTurnoverRate: BigDecimal,
    val outOfStockSkuCount: Long,
)
