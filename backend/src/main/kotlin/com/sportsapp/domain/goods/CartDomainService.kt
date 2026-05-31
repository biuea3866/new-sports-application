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
        requirePositiveQuantity(quantity)
        validateProductActive(productId)

        val cart = getOrCreateCart(userId)
        val existingItem = cartItemRepository.findByCartIdAndProductId(cart.id, productId)
        val totalQuantity = (existingItem?.quantity ?: 0) + quantity
        validateStockSufficient(productId, totalQuantity)

        if (existingItem != null) {
            existingItem.addQuantity(quantity)
            cartItemRepository.save(existingItem)
        } else {
            cartItemRepository.save(CartItem(cartId = cart.id, productId = productId, quantity = quantity))
        }

        return cart to cartItemRepository.findByCartId(cart.id)
    }

    fun updateItem(userId: Long, itemId: Long, newQuantity: Int): Pair<Cart, List<CartItem>> {
        requirePositiveQuantity(newQuantity)

        val cart = getOrCreateCart(userId)
        val item = cartItemRepository.findById(itemId)
            ?: throw ResourceNotFoundException("CartItem", itemId)
        if (item.cartId != cart.id) throw CartAccessDeniedException(itemId)

        validateStockSufficient(item.productId, newQuantity)
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

    private fun requirePositiveQuantity(quantity: Int) {
        if (quantity <= 0) throw InvalidQuantityException(quantity)
    }

    private fun validateProductActive(productId: Long) {
        val product = productRepository.findById(productId)
            ?: throw ResourceNotFoundException("Product", productId)
        if (product.status != ProductStatus.ACTIVE) throw ProductInactiveException(productId)
    }

    private fun validateStockSufficient(productId: Long, requiredQuantity: Int) {
        val stock = stockRepository.findByProductId(productId)
            ?: throw ResourceNotFoundException("Stock", productId)
        if (stock.quantity < requiredQuantity) throw OutOfStockException(productId, requiredQuantity, stock.quantity)
    }
}
