package com.sportsapp.infrastructure.goods.mysql

import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.goods.repository.GoodsOrderCustomRepository
import com.sportsapp.domain.goods.entity.GoodsOrderStatus
import com.sportsapp.domain.goods.entity.QGoodsOrder.goodsOrder
import com.sportsapp.domain.goods.entity.QGoodsOrderItem.goodsOrderItem
import com.sportsapp.domain.goods.entity.QProduct.product
import java.math.BigDecimal
import java.time.ZonedDateTime
import org.springframework.stereotype.Repository

@Repository
class GoodsOrderCustomRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : GoodsOrderCustomRepository {

    override fun countConfirmedByProductOwnerUserId(ownerUserId: Long): Long =
        queryFactory.select(goodsOrder.id.countDistinct())
                    .from(goodsOrder)
                    .join(goodsOrderItem).on(goodsOrderItem.orderId.eq(goodsOrder.id))
                    .join(product).on(product.id.eq(goodsOrderItem.productId))
                    .where(
                        product.ownerId.eq(ownerUserId),
                        goodsOrder.status.eq(GoodsOrderStatus.CONFIRMED),
                        goodsOrder.deletedAt.isNull,
                        goodsOrderItem.deletedAt.isNull,
                        product.deletedAt.isNull,
                    )
                    .fetchOne() ?: 0L

    override fun sumRevenueByProductOwnerUserId(ownerUserId: Long): BigDecimal {
        val result = queryFactory.select(
            goodsOrderItem.unitPrice.multiply(goodsOrderItem.quantity.castToNum(BigDecimal::class.java)).sum()
        )
                    .from(goodsOrderItem)
                    .join(goodsOrder).on(goodsOrder.id.eq(goodsOrderItem.orderId))
                    .join(product).on(product.id.eq(goodsOrderItem.productId))
                    .where(
                        product.ownerId.eq(ownerUserId),
                        goodsOrder.status.eq(GoodsOrderStatus.CONFIRMED),
                        goodsOrder.deletedAt.isNull,
                        goodsOrderItem.deletedAt.isNull,
                        product.deletedAt.isNull,
                    )
                    .fetchOne()
        return result ?: BigDecimal.ZERO
    }

    override fun sumRevenueByProductOwnerUserIdAndDateRange(
        ownerUserId: Long,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): BigDecimal {
        val result = queryFactory.select(
            goodsOrderItem.unitPrice.multiply(goodsOrderItem.quantity.castToNum(BigDecimal::class.java)).sum()
        )
                    .from(goodsOrderItem)
                    .join(goodsOrder).on(goodsOrder.id.eq(goodsOrderItem.orderId))
                    .join(product).on(product.id.eq(goodsOrderItem.productId))
                    .where(
                        product.ownerId.eq(ownerUserId),
                        goodsOrder.status.eq(GoodsOrderStatus.CONFIRMED),
                        goodsOrder.createdAt.goe(from),
                        goodsOrder.createdAt.loe(to),
                        goodsOrder.deletedAt.isNull,
                        goodsOrderItem.deletedAt.isNull,
                        product.deletedAt.isNull,
                    )
                    .fetchOne()
        return result ?: BigDecimal.ZERO
    }
}
