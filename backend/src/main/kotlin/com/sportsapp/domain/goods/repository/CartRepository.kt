package com.sportsapp.domain.goods.repository
import com.sportsapp.domain.goods.entity.Cart

interface CartRepository {
    fun save(cart: Cart): Cart
    fun findActiveByUserId(userId: Long): Cart?
    fun findByUserId(userId: Long): Cart?
}
