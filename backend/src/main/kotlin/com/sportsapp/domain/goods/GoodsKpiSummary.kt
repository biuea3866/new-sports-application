package com.sportsapp.domain.goods

import java.math.BigDecimal

data class GoodsKpiSummary(
    val dailyRevenueTotal: BigDecimal,
    val inventoryTurnoverRate: BigDecimal,
    val outOfStockSkuCount: Long,
)
