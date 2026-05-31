package com.sportsapp.domain.goods

interface CartRepository {
    fun save(cart: Cart): Cart
    fun saveAll(carts: List<Cart>): List<Cart>
    fun findByUserId(userId: Long): Cart?
}
