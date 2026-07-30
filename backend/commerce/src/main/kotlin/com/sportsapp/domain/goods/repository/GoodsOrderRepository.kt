package com.sportsapp.domain.goods.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import com.sportsapp.domain.goods.entity.GoodsOrder

interface GoodsOrderRepository {
    fun save(order: GoodsOrder): GoodsOrder
    fun findById(id: Long): GoodsOrder?
    fun findByIdempotencyKey(idempotencyKey: String): GoodsOrder?
    fun findByUserId(userId: Long, pageable: Pageable): Page<GoodsOrder>
}
