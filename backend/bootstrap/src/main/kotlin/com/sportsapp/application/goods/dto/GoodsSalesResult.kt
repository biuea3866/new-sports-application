package com.sportsapp.application.goods.dto

import java.math.BigDecimal

data class GoodsSalesResult(
    val ownerUserId: Long,
    val activeProductCount: Long,
    val outOfStockProductCount: Long,
    val confirmedOrderCount: Long,
    val totalRevenue: BigDecimal,
)
