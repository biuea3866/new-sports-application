package com.sportsapp.domain.goods.repository
import com.sportsapp.domain.goods.entity.Stock

interface StockRepository {
    fun save(stock: Stock): Stock
    fun findByProductId(productId: Long): Stock?
    fun countOutOfStockByOwnerId(ownerId: Long): Long
}
