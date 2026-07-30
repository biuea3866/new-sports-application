package com.sportsapp.infrastructure.goods.mysql

import com.querydsl.core.Tuple
import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.goods.dto.GoodsOrderWithTitle
import com.sportsapp.domain.goods.entity.GoodsOrder
import com.sportsapp.domain.goods.repository.GoodsOrderCustomRepository
import com.sportsapp.domain.goods.entity.GoodsOrderStatus
import com.sportsapp.domain.goods.entity.QGoodsOrder.goodsOrder
import com.sportsapp.domain.goods.entity.QGoodsOrderItem.goodsOrderItem
import com.sportsapp.domain.goods.entity.QProduct.product
import java.math.BigDecimal
import java.time.ZonedDateTime
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository

@Repository
class GoodsOrderCustomRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : GoodsOrderCustomRepository {

    /**
     * `goods_orders`→`goods_order_items`→`products` 조인으로 대표 상품명(title)을 함께 반환한다
     * (TDD "주문 표시명 확보 방식" — 둘 다 goods 동일 컨텍스트라 R1 위반 없음).
     * 항목 목록은 N+1 없이 orderId IN 절 배치 조회로 채운다.
     */
    override fun findBy(userId: Long, pageable: Pageable): Page<GoodsOrderWithTitle> {
        val orderCondition = goodsOrder.userId.eq(userId).and(goodsOrder.deletedAt.isNull)

        val orders = queryFactory.selectFrom(goodsOrder)
            .where(orderCondition)
            .orderBy(goodsOrder.createdAt.desc())
            .offset(pageable.offset)
            .limit(pageable.pageSize.toLong())
            .fetch()

        if (orders.isEmpty()) return PageImpl(emptyList(), pageable, 0)

        val itemsByOrderId = fetchItemsByOrderId(orders.map { it.id })
        val content = orders.map { order ->
            GoodsOrderWithTitle(order = order, title = buildTitle(itemsByOrderId[order.id].orEmpty()))
        }

        val total = queryFactory.select(goodsOrder.count())
            .from(goodsOrder)
            .where(orderCondition)
            .fetchOne() ?: 0L

        return PageImpl(content, pageable, total)
    }

    private fun fetchItemsByOrderId(orderIds: List<Long>): Map<Long, List<Tuple>> =
        queryFactory.select(goodsOrderItem.orderId, product.name)
            .from(goodsOrderItem)
            .leftJoin(product).on(product.id.eq(goodsOrderItem.productId).and(product.deletedAt.isNull))
            .where(goodsOrderItem.orderId.`in`(orderIds).and(goodsOrderItem.deletedAt.isNull))
            .orderBy(goodsOrderItem.id.asc())
            .fetch()
            .groupBy { it.get(goodsOrderItem.orderId) as Long }

    /**
     * 주문 상세(단건)용 — [findBy]와 동일한 [fetchItemsByOrderId]·[buildTitle]을 재사용해
     * 리스트·상세의 title 조합 방식을 일치시킨다(TDD "주문 표시명 확보 방식").
     */
    override fun findTitleFor(orderId: Long): String =
        buildTitle(fetchItemsByOrderId(listOf(orderId))[orderId].orEmpty())

    /** 대표 항목의 상품명이 없으면(삭제·부재) 빈 title로 방어 반환한다(엣지). */
    private fun buildTitle(items: List<Tuple>): String {
        if (items.isEmpty()) return ""
        val representativeName = items.first().get(product.name) ?: return ""
        return if (items.size > 1) "$representativeName 외 ${items.size - 1}건" else representativeName
    }

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
