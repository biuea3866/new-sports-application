package com.sportsapp.infrastructure.goods.mysql

import com.sportsapp.domain.goods.entity.GoodsOrder
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface GoodsOrderJpaRepository : JpaRepository<GoodsOrder, Long> {
    fun findByIdempotencyKey(idempotencyKey: String): GoodsOrder?
    fun findAllByUserIdAndDeletedAtIsNull(userId: Long, pageable: Pageable): Page<GoodsOrder>
}
