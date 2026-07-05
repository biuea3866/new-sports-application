package com.sportsapp.domain.goods.service

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.goods.dto.GoodsKpiSummary
import com.sportsapp.domain.goods.dto.PopularProductSnapshot
import com.sportsapp.domain.goods.dto.ProductWithStock
import com.sportsapp.domain.goods.entity.GoodsOrder
import com.sportsapp.domain.goods.entity.GoodsOrderItem
import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.entity.ProductStatus
import com.sportsapp.domain.goods.entity.Stock
import com.sportsapp.domain.goods.exception.EmptyOrderException
import com.sportsapp.domain.goods.exception.GoodsOrderNotFoundException
import com.sportsapp.domain.goods.repository.GoodsOrderCustomRepository
import com.sportsapp.domain.goods.repository.GoodsOrderItemRepository
import com.sportsapp.domain.goods.repository.GoodsOrderRepository
import com.sportsapp.domain.goods.repository.LimitedDropRepository
import com.sportsapp.domain.goods.repository.PopularProductsCache
import com.sportsapp.domain.goods.repository.ProductCustomRepository
import com.sportsapp.domain.goods.repository.ProductRepository
import com.sportsapp.domain.goods.repository.StockRepository
import com.sportsapp.domain.goods.vo.OrderItemInput
import com.sportsapp.domain.goods.vo.ProductCategory
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

@Service
class GoodsDomainService(
    private val productRepository: ProductRepository,
    private val stockRepository: StockRepository,
    private val productCustomRepository: ProductCustomRepository,
    private val popularProductsCache: PopularProductsCache,
    private val goodsOrderRepository: GoodsOrderRepository,
    private val goodsOrderItemRepository: GoodsOrderItemRepository,
    private val goodsOrderCustomRepository: GoodsOrderCustomRepository,
    private val limitedDropRepository: LimitedDropRepository,
) {
    fun search(
        category: ProductCategory?,
        keyword: String?,
        priceMin: BigDecimal?,
        priceMax: BigDecimal?,
        pageable: Pageable,
    ): Page<ProductWithStock> {
        val page = productCustomRepository.search(category, keyword, priceMin, priceMax, pageable)
        return enrichWithLimitedDropId(page)
    }

    /**
     * `/products` 목록 응답의 한정판 진입점 배너용 — 페이지 내 상품 id를 한 번에 모아
     * [LimitedDropRepository.findOpenByProductIds]로 배치 조회한다(N+1 방지).
     *
     * 한 상품에 활성 회차가 2건 이상이면 openAt이 가장 최신인 회차를 선택한다 —
     * 단건 조회 경로([LimitedDropRepository.findOpenByProductId]의
     * `OrderByOpenAtDesc`)와 선택 기준을 일치시켜, 목록과 상세의 limitedDropId가
     * 달라지지 않게 한다(코드 리뷰 p3).
     */
    private fun enrichWithLimitedDropId(page: Page<ProductWithStock>): Page<ProductWithStock> {
        if (page.content.isEmpty()) return page
        val productIds = page.content.map { it.product.id }
        val dropIdByProductId = limitedDropRepository.findOpenByProductIds(productIds)
            .sortedByDescending { it.openAt }
            .distinctBy { it.productId }
            .associateBy({ it.productId }, { it.id })
        val enrichedContent = page.content.map { it.copy(limitedDropId = dropIdByProductId[it.product.id]) }
        return PageImpl(enrichedContent, page.pageable, page.totalElements)
    }

    fun deductStock(productId: Long, quantity: Int) {
        productRepository.findById(productId) ?: throw ResourceNotFoundException("Product", productId)
        val stock = stockRepository.findByProductId(productId)
            ?: throw ResourceNotFoundException("Stock", productId)
        stock.deduct(quantity)
        stockRepository.save(stock)
    }

    fun restoreStock(productId: Long, quantity: Int) {
        val stock = stockRepository.findByProductId(productId)
            ?: throw ResourceNotFoundException("Stock", productId)
        stock.restore(quantity)
        stockRepository.save(stock)
    }

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

    fun createPendingOrder(userId: Long, items: List<OrderItemInput>, idempotencyKey: String): GoodsOrder {
        goodsOrderRepository.findByIdempotencyKey(idempotencyKey)?.let { return it }
        if (items.isEmpty()) throw EmptyOrderException()
        val products = items.associate { item -> item.productId to validateAndDeductStock(item) }
        val totalAmount = items.fold(BigDecimal.ZERO) { acc, item ->
            acc.add(products.getValue(item.productId).price.multiply(BigDecimal(item.quantity)))
        }
        val order = goodsOrderRepository.save(GoodsOrder.create(userId, totalAmount, idempotencyKey))
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

    fun cancelPendingOrder(orderId: Long) {
        val order = goodsOrderRepository.findById(orderId)
            ?: throw GoodsOrderNotFoundException(orderId)
        order.cancel()
        goodsOrderRepository.save(order)
        val items = goodsOrderItemRepository.findByOrderId(orderId)
        items.forEach { item ->
            val stock = stockRepository.findByProductId(item.productId)
                ?: throw ResourceNotFoundException("Stock", item.productId)
            stock.restore(item.quantity)
            stockRepository.save(stock)
        }
        items.forEach { it.softDelete(null) }
        if (items.isNotEmpty()) goodsOrderItemRepository.saveAll(items)
    }

    fun markPaid(orderId: Long, paymentId: Long): GoodsOrder {
        val order = goodsOrderRepository.findById(orderId)
            ?: throw GoodsOrderNotFoundException(orderId)
        order.markPaid(paymentId)
        return goodsOrderRepository.save(order)
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

    fun createProduct(
        name: String,
        category: ProductCategory,
        price: java.math.BigDecimal,
        description: String,
        imageUrl: String,
        ownerUserId: Long,
    ): Pair<Product, Stock> {
        val product = productRepository.save(
            Product.create(
                name = name,
                category = category,
                price = price,
                description = description,
                imageUrl = imageUrl,
                ownerUserId = ownerUserId,
            )
        )
        val stock = stockRepository.save(Stock(productId = product.id, quantity = 0))
        return product to stock
    }

    fun getProductWithStock(productId: Long): ProductWithStock {
        val product = productRepository.findByIdAndDeletedAtIsNull(productId)
            ?: throw ResourceNotFoundException("Product", productId)
        val stockQuantity = stockRepository.findByProductId(productId)?.quantity ?: 0
        val limitedDropId = limitedDropRepository.findOpenByProductId(productId)?.id
        return ProductWithStock(product = product, stockQuantity = stockQuantity, limitedDropId = limitedDropId)
    }

    fun getProductByIdAndOwnerId(productId: Long, ownerUserId: Long): ProductWithStock {
        val productEntity = productRepository.findById(productId)
            ?: throw ResourceNotFoundException("Product", productId)
        productEntity.requireOwnedBy(ownerUserId)
        val stockQuantity = stockRepository.findByProductId(productId)?.quantity ?: 0
        return ProductWithStock(product = productEntity, stockQuantity = stockQuantity)
    }

    fun updateProduct(
        productId: Long,
        ownerUserId: Long,
        name: String?,
        category: ProductCategory?,
        price: java.math.BigDecimal?,
        description: String?,
        imageUrl: String?,
    ): ProductWithStock {
        val productEntity = productRepository.findById(productId)
            ?: throw ResourceNotFoundException("Product", productId)
        productEntity.requireOwnedBy(ownerUserId)
        productEntity.update(name, category, price, description, imageUrl)
        val saved = productRepository.save(productEntity)
        val stockQuantity = stockRepository.findByProductId(productId)?.quantity ?: 0
        return ProductWithStock(product = saved, stockQuantity = stockQuantity)
    }

    fun activateProduct(productId: Long, ownerUserId: Long): Product {
        val productEntity = productRepository.findById(productId)
            ?: throw ResourceNotFoundException("Product", productId)
        productEntity.requireOwnedBy(ownerUserId)
        productEntity.activate()
        return productRepository.save(productEntity)
    }

    fun activateProductWithStock(productId: Long, ownerUserId: Long): ProductWithStock {
        val productEntity = activateProduct(productId, ownerUserId)
        val stockQuantity = stockRepository.findByProductId(productId)?.quantity ?: 0
        return ProductWithStock(product = productEntity, stockQuantity = stockQuantity)
    }

    fun deactivateProduct(productId: Long, ownerUserId: Long): Product {
        val productEntity = productRepository.findById(productId)
            ?: throw ResourceNotFoundException("Product", productId)
        productEntity.requireOwnedBy(ownerUserId)
        productEntity.deactivate()
        return productRepository.save(productEntity)
    }

    fun deactivateProductWithStock(productId: Long, ownerUserId: Long): ProductWithStock {
        val productEntity = deactivateProduct(productId, ownerUserId)
        val stockQuantity = stockRepository.findByProductId(productId)?.quantity ?: 0
        return ProductWithStock(product = productEntity, stockQuantity = stockQuantity)
    }

    fun listMyProducts(ownerUserId: Long, pageable: Pageable): Page<ProductWithStock> =
        productCustomRepository.findByOwnerId(ownerUserId, pageable)

    fun countActiveProductsByOwnerId(ownerId: Long): Long =
        productRepository.countByOwnerIdAndStatus(ownerId, ProductStatus.ACTIVE)

    fun countOutOfStockProductsByOwnerId(ownerId: Long): Long =
        stockRepository.countOutOfStockByOwnerId(ownerId)

    fun restoreProductStock(productId: Long, ownerUserId: Long, quantity: Int) {
        val productEntity = productRepository.findById(productId)
            ?: throw ResourceNotFoundException("Product", productId)
        productEntity.requireOwnedBy(ownerUserId)
        restoreStock(productId, quantity)
    }

    fun countConfirmedOrdersByOwnerUserId(ownerUserId: Long): Long =
        goodsOrderCustomRepository.countConfirmedByProductOwnerUserId(ownerUserId)

    fun sumRevenueByOwnerUserId(ownerUserId: Long): BigDecimal =
        goodsOrderCustomRepository.sumRevenueByProductOwnerUserId(ownerUserId)

    fun aggregateGoodsKpi(ownerUserId: Long, from: ZonedDateTime, to: ZonedDateTime): GoodsKpiSummary {
        val periodRevenue = goodsOrderCustomRepository.sumRevenueByProductOwnerUserIdAndDateRange(ownerUserId, from, to)
        val outOfStockSkuCount = stockRepository.countOutOfStockByOwnerId(ownerUserId)
        val activeProductCount = productRepository.countByOwnerIdAndStatus(ownerUserId, ProductStatus.ACTIVE)

        val dayCount = ChronoUnit.DAYS.between(from.toLocalDate(), to.toLocalDate()).coerceAtLeast(1)
        val dailyRevenueTotal = periodRevenue.divide(BigDecimal(dayCount), 2, RoundingMode.HALF_UP)

        val inventoryTurnoverRate = if (activeProductCount > 0) {
            periodRevenue.divide(BigDecimal(activeProductCount), 2, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO

        return GoodsKpiSummary(
            dailyRevenueTotal = dailyRevenueTotal,
            inventoryTurnoverRate = inventoryTurnoverRate,
            outOfStockSkuCount = outOfStockSkuCount,
        )
    }

    companion object {
        private const val POPULAR_LIMIT = 20
    }
}
