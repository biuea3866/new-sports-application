package com.sportsapp.infrastructure.goods.mysql

import com.sportsapp.domain.goods.entity.GoodsOrderItem
import org.springframework.data.jpa.repository.JpaRepository

interface GoodsOrderItemJpaRepository : JpaRepository<GoodsOrderItem, Long> {
    fun findAllByOrderIdAndDeletedAtIsNull(orderId: Long): List<GoodsOrderItem>
}
