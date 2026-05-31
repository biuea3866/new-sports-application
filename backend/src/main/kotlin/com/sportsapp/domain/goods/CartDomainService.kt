package com.sportsapp.domain.goods

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import org.springframework.stereotype.Service

@Service
class CartDomainService(
    private val cartRepository: CartRepository,
    private val cartItemRepository: CartItemRepository,
    private val productRepository: ProductRepository,
    private val stockRepository: StockRepository,
) {

    fun getOrCreateCart(userId: Long): Cart =
        cartRepository.findActiveByUserId(userId) ?: cartRepository.save(Cart(userId = userId))

    fun getCartWithItems(userId: Long): Pair<Cart, List<CartItem>> {
        val cart = getOrCreateCart(userId)
        return cart to cartItemRepository.findByCartId(cart.id)
    }

    fun addItem(userId: Long, productId: Long, quantity: Int): Pair<Cart, List<CartItem>> {
        val product = productRepository.findById(productId)
            ?: throw ResourceNotFoundException("Product", productId)
        product.requireActive()

        val cart = getOrCreateCart(userId)
        val existingItem = cartItemRepository.findByCartIdAndProductId(cart.id, productId)
        val totalQuantity = (existingItem?.quantity ?: 0) + quantity

        val stock = stockRepository.findByProductId(productId)
            ?: throw ResourceNotFoundException("Stock", productId)
        stock.requireSufficient(totalQuantity)

        if (existingItem != null) {
            existingItem.addQuantity(quantity)
            cartItemRepository.save(existingItem)
        } else {
            cartItemRepository.save(CartItem(cartId = cart.id, productId = productId, quantity = quantity))
        }

        return cart to cartItemRepository.findByCartId(cart.id)
    }

    fun updateItem(userId: Long, itemId: Long, newQuantity: Int): Pair<Cart, List<CartItem>> {
        val cart = getOrCreateCart(userId)
        val item = cartItemRepository.findById(itemId)
            ?: throw ResourceNotFoundException("CartItem", itemId)
        if (item.cartId != cart.id) throw CartAccessDeniedException(itemId)

        val stock = stockRepository.findByProductId(item.productId)
            ?: throw ResourceNotFoundException("Stock", item.productId)
        stock.requireSufficient(newQuantity)
        item.updateQuantity(newQuantity)
        cartItemRepository.save(item)

        return cart to cartItemRepository.findByCartId(cart.id)
    }

    fun removeItem(userId: Long, itemId: Long): Pair<Cart, List<CartItem>> {
        val cart = getOrCreateCart(userId)
        val item = cartItemRepository.findById(itemId)
            ?: throw ResourceNotFoundException("CartItem", itemId)
        if (item.cartId != cart.id) throw CartAccessDeniedException(itemId)

        item.softDelete(userId)
        cartItemRepository.save(item)

        return cart to cartItemRepository.findByCartId(cart.id)
    }

    fun clearCart(userId: Long) {
        val cart = getOrCreateCart(userId)
        val items = cartItemRepository.findAllByCartId(cart.id)
        items.forEach { it.softDelete(userId) }
        cartItemRepository.saveAll(items)
    }
}
