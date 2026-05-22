package com.sportsapp.application.goods

import java.math.BigDecimal

data class GoodsSalesResponse(
    val ownerUserId: Long,
    val activeProductCount: Long,
    val outOfStockProductCount: Long,
    val confirmedOrderCount: Long,
    val totalRevenue: BigDecimal,
)
