package com.sportsapp.domain.goods

import java.time.ZonedDateTime

interface CustomGoodsRepository {
    fun aggregateSales(
        ownerUserId: Long,
        productId: Long?,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): List<GoodsSalesSummary>

    fun findInventory(ownerUserId: Long, lowStockOnly: Boolean): List<InventoryItem>
}
