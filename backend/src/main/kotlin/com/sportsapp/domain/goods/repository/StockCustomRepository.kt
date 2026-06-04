package com.sportsapp.domain.goods.repository

interface StockCustomRepository {
    fun countOutOfStockByOwnerId(ownerId: Long): Long
}
