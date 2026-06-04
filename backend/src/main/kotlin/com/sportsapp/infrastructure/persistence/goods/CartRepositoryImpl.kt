package com.sportsapp.infrastructure.persistence.goods

import com.sportsapp.domain.goods.Cart
import com.sportsapp.domain.goods.CartRepository
import org.springframework.stereotype.Repository

@Repository
class CartRepositoryImpl(
    private val cartJpaRepository: CartJpaRepository,
) : CartRepository {

    override fun save(cart: Cart): Cart {
        if (cart.isDeleted && cart.activeMarker != null) {
            cart.markInactive()
        }
        val saved = cartJpaRepository.save(cart)
        if (saved.activeMarker == null && !saved.isDeleted) {
            saved.markActive()
            cartJpaRepository.save(saved)
        }
        return saved
    }

    override fun findActiveByUserId(userId: Long): Cart? = cartJpaRepository.findByUserIdAndDeletedAtIsNull(userId)

    override fun findByUserId(userId: Long): Cart? = cartJpaRepository.findByUserIdAndDeletedAtIsNull(userId)
}
