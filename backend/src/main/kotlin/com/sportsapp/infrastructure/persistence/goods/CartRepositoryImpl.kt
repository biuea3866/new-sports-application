package com.sportsapp.infrastructure.persistence.goods

import com.sportsapp.domain.goods.Cart
import com.sportsapp.domain.goods.CartRepository
import org.springframework.stereotype.Repository

@Repository
class CartRepositoryImpl(
    private val cartJpaRepository: CartJpaRepository,
) : CartRepository {

    override fun save(cart: Cart): Cart = cartJpaRepository.save(cart)

    override fun findByUserId(userId: Long): Cart? = cartJpaRepository.findByUserId(userId)
}
