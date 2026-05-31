package com.sportsapp.infrastructure.persistence.goods

import com.sportsapp.domain.goods.GoodsOrderItem
import org.springframework.data.jpa.repository.JpaRepository

interface GoodsOrderItemJpaRepository : JpaRepository<GoodsOrderItem, Long> {
    fun findAllByOrder_IdAndDeletedAtIsNull(orderId: Long): List<GoodsOrderItem>
}
