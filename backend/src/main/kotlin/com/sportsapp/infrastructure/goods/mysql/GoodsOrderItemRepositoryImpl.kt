package com.sportsapp.infrastructure.goods.mysql

import com.sportsapp.domain.goods.entity.GoodsOrderItem
import com.sportsapp.domain.goods.repository.GoodsOrderItemRepository
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
