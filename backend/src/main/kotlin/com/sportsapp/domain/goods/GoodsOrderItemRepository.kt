package com.sportsapp.domain.goods

interface GoodsOrderItemRepository {
    fun saveAll(items: List<GoodsOrderItem>): List<GoodsOrderItem>
    fun findByOrderId(orderId: Long): List<GoodsOrderItem>
}
