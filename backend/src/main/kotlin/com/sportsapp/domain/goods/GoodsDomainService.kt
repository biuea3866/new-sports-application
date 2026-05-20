package com.sportsapp.domain.goods

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import java.math.BigDecimal
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GoodsDomainService(
    private val productRepository: ProductRepository,
    private val stockRepository: StockRepository,
    private val customProductRepository: CustomProductRepository,
    private val popularProductsCache: PopularProductsCache,
    private val goodsOrderRepository: GoodsOrderRepository,
    private val goodsOrderItemRepository: GoodsOrderItemRepository,
) {
    @Transactional(readOnly = true)
    fun search(
        category: ProductCategory?,
        keyword: String?,
        priceMin: BigDecimal?,
        priceMax: BigDecimal?,
        pageable: Pageable,
    ): Page<ProductWithStock> =
        customProductRepository.search(category, keyword, priceMin, priceMax, pageable)

    // TODO: UseCase 신설 시 @Transactional 을 UseCase 레이어로 이동. DomainService 에 @Transactional 유지는 UseCase 미존재 과도기 한정.
    @Transactional
    fun deductStock(productId: Long, quantity: Int) {
        productRepository.findById(productId) ?: throw ResourceNotFoundException("Product", productId)
        val stock = stockRepository.findByProductId(productId)
            ?: throw ResourceNotFoundException("Stock", productId)
        stock.deduct(quantity)
        stockRepository.save(stock)
    }

    @Transactional
    fun restoreStock(productId: Long, quantity: Int) {
        val stock = stockRepository.findByProductId(productId)
            ?: throw ResourceNotFoundException("Stock", productId)
        stock.restore(quantity)
        stockRepository.save(stock)
    }

    @Transactional(readOnly = true)
    fun getPopular(category: ProductCategory): List<PopularProductSnapshot> {
        popularProductsCache.get(category)?.let { return it }
        // TODO(GOODS-05): 판매 수 집계 기반 정렬로 교체
        val snapshots = productRepository.findByCategoryAndStatus(category, ProductStatus.ACTIVE)
            .sortedByDescending { it.createdAt }
            .take(POPULAR_LIMIT)
            .map { PopularProductSnapshot.of(it) }
        popularProductsCache.put(category, snapshots)
        return snapshots
    }

    fun invalidatePopularCache(category: ProductCategory) {
        popularProductsCache.invalidate(category)
    }

    fun createPendingOrder(userId: Long, items: List<OrderItemInput>): GoodsOrder {
        if (items.isEmpty()) throw EmptyOrderException()
        val products = items.associate { item -> item.productId to validateAndDeductStock(item) }
        val totalAmount = items.fold(BigDecimal.ZERO) { acc, item ->
            acc.add(products.getValue(item.productId).price.multiply(BigDecimal(item.quantity)))
        }
        val order = goodsOrderRepository.save(GoodsOrder.create(userId, totalAmount))
        val orderItems = items.map { item ->
            GoodsOrderItem(
                orderId = order.id,
                productId = item.productId,
                quantity = item.quantity,
                unitPrice = products.getValue(item.productId).price,
            )
        }
        goodsOrderItemRepository.saveAll(orderItems)
        return order
    }

    private fun validateAndDeductStock(item: OrderItemInput): Product {
        val product = productRepository.findById(item.productId)
            ?: throw ResourceNotFoundException("Product", item.productId)
        product.requireActive()
        val stock = stockRepository.findByProductId(item.productId)
            ?: throw ResourceNotFoundException("Stock", item.productId)
        stock.requireSufficient(item.quantity)
        stock.deduct(item.quantity)
        stockRepository.save(stock)
        return product
    }

    fun getOrder(userId: Long, orderId: Long): Pair<GoodsOrder, List<GoodsOrderItem>> {
        val order = goodsOrderRepository.findById(orderId)
            ?: throw GoodsOrderNotFoundException(orderId)
        order.requireOwnedBy(userId)
        val items = goodsOrderItemRepository.findByOrderId(orderId)
        return order to items
    }

    fun listMyOrders(userId: Long, pageable: Pageable): Page<GoodsOrder> =
        goodsOrderRepository.findByUserId(userId, pageable)

    companion object {
        private const val POPULAR_LIMIT = 20
    }
}
