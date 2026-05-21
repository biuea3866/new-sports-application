package com.sportsapp.infrastructure.persistence.goods

import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.goods.CustomGoodsRepository
import com.sportsapp.domain.goods.GoodsOrderStatus
import com.sportsapp.domain.goods.GoodsSalesSummary
import com.sportsapp.domain.goods.InventoryItem
import com.sportsapp.domain.goods.QGoodsOrder.goodsOrder
import com.sportsapp.domain.goods.QGoodsOrderItem.goodsOrderItem
import com.sportsapp.domain.goods.QProduct.product
import com.sportsapp.domain.goods.QStock.stock
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository
import java.time.ZonedDateTime

private const val LOW_STOCK_THRESHOLD = 5

@Repository
class CustomGoodsRepositoryImpl : CustomGoodsRepository {

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    private val queryFactory: JPAQueryFactory
        get() = JPAQueryFactory(entityManager)

    override fun aggregateSales(
        ownerUserId: Long,
        productId: Long?,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): List<GoodsSalesSummary> {
        return queryFactory.select(
            Projections.constructor(
                GoodsSalesSummary::class.java,
                product.id,
                product.name,
                goodsOrderItem.unitPrice.multiply(goodsOrderItem.quantity.castToNum(java.math.BigDecimal::class.java)).sum(),
                goodsOrder.id.count(),
            )
        )
            .from(goodsOrderItem)
            .join(goodsOrder).on(goodsOrder.id.eq(goodsOrderItem.orderId))
            .join(product).on(product.id.eq(goodsOrderItem.productId))
            .where(
                product.ownerId.eq(ownerUserId),
                goodsOrder.status.eq(GoodsOrderStatus.CONFIRMED),
                goodsOrder.createdAt.goe(from),
                goodsOrder.createdAt.loe(to),
                productId?.let { goodsOrderItem.productId.eq(it) },
            )
            .groupBy(product.id, product.name)
            .fetch()
    }

    override fun findInventory(ownerUserId: Long, lowStockOnly: Boolean): List<InventoryItem> {
        val results = queryFactory.select(product, stock)
                                  .from(product)
                                  .leftJoin(stock).on(stock.productId.eq(product.id))
                                  .where(
                                      product.ownerId.eq(ownerUserId),
                                      product.deletedAt.isNull,
                                  )
                                  .orderBy(product.name.asc())
                                  .fetch()

        return results
            .map { tuple ->
                val fetchedProduct = requireNotNull(tuple.get(product)) { "product must not be null" }
                val quantity = tuple.get(stock)?.quantity ?: 0
                InventoryItem(
                    productId = fetchedProduct.id,
                    productName = fetchedProduct.name,
                    stockQuantity = quantity,
                    lowStock = quantity <= LOW_STOCK_THRESHOLD,
                )
            }
            .filter { if (lowStockOnly) it.lowStock else true }
    }
}
