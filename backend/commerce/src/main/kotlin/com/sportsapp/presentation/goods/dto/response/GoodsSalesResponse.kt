package com.sportsapp.presentation.goods.dto.response

import com.sportsapp.application.goods.dto.GoodsSalesResult
import java.math.BigDecimal

data class GoodsSalesResponse(
    val ownerUserId: Long,
    val activeProductCount: Long,
    val outOfStockProductCount: Long,
    val confirmedOrderCount: Long,
    val totalRevenue: BigDecimal,
) {
    companion object {
        fun of(result: GoodsSalesResult) = GoodsSalesResponse(
            ownerUserId = result.ownerUserId,
            activeProductCount = result.activeProductCount,
            outOfStockProductCount = result.outOfStockProductCount,
            confirmedOrderCount = result.confirmedOrderCount,
            totalRevenue = result.totalRevenue,
        )
    }
}
