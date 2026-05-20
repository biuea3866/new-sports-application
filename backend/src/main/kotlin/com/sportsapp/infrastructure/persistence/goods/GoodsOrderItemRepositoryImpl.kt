package com.sportsapp.infrastructure.persistence.goods

import com.sportsapp.domain.goods.GoodsOrderItem
import com.sportsapp.domain.goods.GoodsOrderItemRepository
import org.springframework.stereotype.Repository

@Repository
class GoodsOrderItemRepositoryImpl(
    private val goodsOrderItemJpaRepository: GoodsOrderItemJpaRepository,
) : GoodsOrderItemRepository {

    override fun saveAll(items: List<GoodsOrderItem>): List<GoodsOrderItem> =
        goodsOrderItemJpaRepository.saveAll(items)

    override fun findByOrderId(orderId: Long): List<GoodsOrderItem> =
        goodsOrderItemJpaRepository.findAllByOrderIdAndDeletedAtIsNull(orderId)
}
