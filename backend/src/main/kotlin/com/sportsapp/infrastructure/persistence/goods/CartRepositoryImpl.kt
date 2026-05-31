package com.sportsapp.infrastructure.persistence.goods

import com.sportsapp.domain.goods.Cart
import com.sportsapp.domain.goods.CartRepository
import org.springframework.stereotype.Repository

@Repository
class CartRepositoryImpl(
    private val cartJpaRepository: CartJpaRepository,
) : CartRepository {

    companion object {
        private const val ACTIVE_MARKER = 1L
    }

    override fun save(cart: Cart): Cart {
        if (cart.isDeleted && cart.activeMarker != null) {
            cart.activeMarker = null
        }
        val saved = cartJpaRepository.save(cart)
        if (saved.activeMarker == null && !saved.isDeleted) {
            saved.activeMarker = ACTIVE_MARKER
            cartJpaRepository.save(saved)
        }
        return saved
    }

    override fun saveAll(carts: List<Cart>): List<Cart> = cartJpaRepository.saveAll(carts)

    override fun findByUserId(userId: Long): Cart? {
        val activeCarts = cartJpaRepository.findAllByUserIdAndDeletedAtIsNull(userId)
        if (activeCarts.size <= 1) return activeCarts.firstOrNull()

        val newest = activeCarts.maxBy { it.id }
        val duplicates = activeCarts.filter { it.id != newest.id }
        duplicates.forEach { duplicate ->
            duplicate.softDelete(userId = null)
            duplicate.activeMarker = null
        }
        cartJpaRepository.saveAll(duplicates)
        return newest
    }
}
