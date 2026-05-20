package com.sportsapp.infrastructure.persistence.goods

import com.sportsapp.domain.goods.CartItem
import com.sportsapp.domain.goods.CartItemRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class CartItemRepositoryImpl(
    private val cartItemJpaRepository: CartItemJpaRepository,
) : CartItemRepository {

    override fun save(cartItem: CartItem): CartItem = cartItemJpaRepository.save(cartItem)

    override fun saveAll(cartItems: List<CartItem>): List<CartItem> = cartItemJpaRepository.saveAll(cartItems)

    override fun findById(id: Long): CartItem? = cartItemJpaRepository.findByIdOrNull(id)

    override fun findByCartId(cartId: Long): List<CartItem> =
        cartItemJpaRepository.findAllByCartIdAndDeletedAtIsNull(cartId)

    override fun findByCartIdAndProductId(cartId: Long, productId: Long): CartItem? =
        cartItemJpaRepository.findByCartIdAndProductIdAndDeletedAtIsNull(cartId, productId)

    override fun findAllByCartId(cartId: Long): List<CartItem> =
        cartItemJpaRepository.findAllByCartId(cartId).filter { !it.isDeleted }
}
