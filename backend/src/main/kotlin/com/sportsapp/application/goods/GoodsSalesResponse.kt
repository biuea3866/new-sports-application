package com.sportsapp.application.goods

import com.sportsapp.domain.goods.GoodsSalesSummary
import java.math.BigDecimal

data class GoodsSalesSummaryItem(
    val productId: Long,
    val productName: String,
    val totalRevenue: BigDecimal,
    val orderCount: Long,
) {
    companion object {
        fun of(summary: GoodsSalesSummary): GoodsSalesSummaryItem = GoodsSalesSummaryItem(
            productId = summary.productId,
            productName = summary.productName,
            totalRevenue = summary.totalRevenue,
            orderCount = summary.orderCount,
        )
    }
}

data class GoodsSalesResponse(
    val items: List<GoodsSalesSummaryItem>,
) {
    companion object {
        fun of(summaries: List<GoodsSalesSummary>): GoodsSalesResponse =
            GoodsSalesResponse(items = summaries.map { GoodsSalesSummaryItem.of(it) })
    }
}
