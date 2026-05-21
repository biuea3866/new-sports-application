package com.sportsapp.domain.goods

import java.math.BigDecimal

data class GoodsSalesSummary(
    val productId: Long,
    val productName: String,
    val totalRevenue: BigDecimal,
    val orderCount: Long,
)
