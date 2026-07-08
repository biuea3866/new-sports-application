package com.sportsapp.infrastructure.goods.mysql

import com.sportsapp.domain.goods.entity.Cart
import com.sportsapp.domain.goods.repository.CartRepository
import org.springframework.stereotype.Repository

@Repository
class CartRepositoryImpl(
    private val cartJpaRepository: CartJpaRepository,
) : CartRepository {

    override fun save(cart: Cart): Cart = cartJpaRepository.save(cart)

    override fun findActiveByUserId(userId: Long): Cart? = cartJpaRepository.findByUserIdAndDeletedAtIsNull(userId)

    override fun findByUserId(userId: Long): Cart? = cartJpaRepository.findByUserIdAndDeletedAtIsNull(userId)
}
