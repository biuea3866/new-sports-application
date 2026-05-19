package com.sportsapp.domain.goods

interface StockRepository {
    fun save(stock: Stock): Stock
    fun findByProductId(productId: Long): Stock?
}
