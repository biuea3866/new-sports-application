package com.sportsapp.domain.goods.repository
import com.sportsapp.domain.goods.entity.GoodsOrderItem

interface GoodsOrderItemRepository {
    fun saveAll(items: List<GoodsOrderItem>): List<GoodsOrderItem>
    fun findByOrderId(orderId: Long): List<GoodsOrderItem>
}
