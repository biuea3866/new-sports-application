package com.sportsapp.domain.goods

interface CartRepository {
    fun save(cart: Cart): Cart
    fun findActiveByUserId(userId: Long): Cart?
    fun findByUserId(userId: Long): Cart?
}
