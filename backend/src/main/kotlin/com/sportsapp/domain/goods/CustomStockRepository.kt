package com.sportsapp.domain.goods

interface CustomStockRepository {
    fun countOutOfStockByOwnerId(ownerId: Long): Long
}
