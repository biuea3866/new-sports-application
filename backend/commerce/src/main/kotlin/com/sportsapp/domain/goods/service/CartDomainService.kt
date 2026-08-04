package com.sportsapp.domain.goods.service

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.goods.entity.Cart
import com.sportsapp.domain.goods.entity.CartItem
import com.sportsapp.domain.goods.exception.CartAccessDeniedException
import com.sportsapp.domain.goods.repository.CartItemRepository
import com.sportsapp.domain.goods.repository.CartRepository
import com.sportsapp.domain.goods.repository.ProductRepository
import com.sportsapp.domain.goods.repository.StockRepository
import com.sportsapp.domain.goods.vo.CartLineItem
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class CartDomainService(
    private val cartRepository: CartRepository,
    private val cartItemRepository: CartItemRepository,
    private val productRepository: ProductRepository,
    private val stockRepository: StockRepository,
) {

    fun getOrCreateCart(userId: Long): Cart =
        cartRepository.findByUserId(userId) ?: cartRepository.save(Cart(userId = userId))

    fun getCartWithItems(userId: Long): Pair<Cart, List<CartItem>> {
        val cart = getOrCreateCart(userId)
        return cart to cartItemRepository.findByCartId(cart.id)
    }

    /**
     * 장바구니 조회 — 담긴 항목에 상품명·단가를 결합해 돌려준다.
     * 화면이 productId 로 "상품 #121" 같은 기술 식별자를 렌더하지 않게 하는 것이 목적이다.
     */
    fun getCartWithLineItems(userId: Long): Pair<Cart, List<CartLineItem>> {
        val (cart, items) = getCartWithItems(userId)
        return cart to toLineItems(items)
    }

    /**
     * 상품을 한 번에 배치 조회해 결합한다 — 항목 수만큼 SELECT 하지 않는다(N+1 방지).
     * 소프트 삭제된 상품은 조회에서 빠지므로, 그 항목은 기본 문구로 방어해 장바구니 전체가
     * 실패하지 않게 한다.
     */
    private fun toLineItems(items: List<CartItem>): List<CartLineItem> {
        if (items.isEmpty()) return emptyList()
        val productsById = productRepository
            .findAllByIdsAndDeletedAtIsNull(items.map { it.productId })
            .associateBy { it.id }
        return items.map { item ->
            val product = productsById[item.productId]
            CartLineItem(
                id = item.id,
                productId = item.productId,
                productName = product?.name ?: UNKNOWN_PRODUCT_NAME,
                productImageUrl = product?.imageUrl ?: "",
                unitPrice = product?.price ?: BigDecimal.ZERO,
                quantity = item.quantity,
            )
        }
    }

    fun addItem(userId: Long, productId: Long, quantity: Int): Pair<Cart, List<CartLineItem>> {
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

        return cart to toLineItems(cartItemRepository.findByCartId(cart.id))
    }

    // ThrowsCount 억제 근거: 세 개 모두 서로 다른 실패를 구분하는 guard clause 다 —
    // 항목 부재(404)·타인 장바구니 접근(403)·재고 레코드 부재(404). 컨벤션이 권장하는
    // early return 형태이고, 합치면 호출자가 404/403 을 구분할 수 없다.
    // 중복(`removeItem` 과 앞 두 guard 가 동일)을 헬퍼로 묶는 리팩토링은 이 경로에 테스트가
    // 0건이라 지금 하지 않는다 — 커버리지 확보 후 진행한다
    // (`MSA 물리분리/후속-리스크-등록부.md` R-22).
    @Suppress("ThrowsCount")
    fun updateItem(userId: Long, itemId: Long, newQuantity: Int): Pair<Cart, List<CartLineItem>> {
        val cart = getOrCreateCart(userId)
        val item = cartItemRepository.findById(itemId)
            ?: throw ResourceNotFoundException("CartItem", itemId)
        if (item.cartId != cart.id) throw CartAccessDeniedException(itemId)

        val stock = stockRepository.findByProductId(item.productId)
            ?: throw ResourceNotFoundException("Stock", item.productId)
        stock.requireSufficient(newQuantity)
        item.updateQuantity(newQuantity)
        cartItemRepository.save(item)

        return cart to toLineItems(cartItemRepository.findByCartId(cart.id))
    }

    fun removeItem(userId: Long, itemId: Long): Pair<Cart, List<CartLineItem>> {
        val cart = getOrCreateCart(userId)
        val item = cartItemRepository.findById(itemId)
            ?: throw ResourceNotFoundException("CartItem", itemId)
        if (item.cartId != cart.id) throw CartAccessDeniedException(itemId)

        item.softDelete(userId)
        cartItemRepository.save(item)

        return cart to toLineItems(cartItemRepository.findByCartId(cart.id))
    }

    fun clearCart(userId: Long) {
        val cart = getOrCreateCart(userId)
        val items = cartItemRepository.findAllByCartId(cart.id)
        items.forEach { it.softDelete(userId) }
        cartItemRepository.saveAll(items)
    }

    companion object {
        /** 상품이 삭제되어 이름을 알 수 없을 때 표시할 기본 문구. */
        const val UNKNOWN_PRODUCT_NAME = "삭제된 상품"
    }
}
