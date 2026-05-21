package com.sportsapp.domain.goods

interface StockCustomRepository {
    fun countOutOfStockByOwnerId(ownerId: Long): Long
}
