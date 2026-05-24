package com.sportsapp.infrastructure.persistence.goods

import com.sportsapp.domain.goods.GoodsOrder
import com.sportsapp.domain.goods.GoodsOrderRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class GoodsOrderRepositoryImpl(
    private val goodsOrderJpaRepository: GoodsOrderJpaRepository,
) : GoodsOrderRepository {

    override fun save(order: GoodsOrder): GoodsOrder =
        goodsOrderJpaRepository.save(order)

    override fun findById(id: Long): GoodsOrder? =
        goodsOrderJpaRepository.findByIdOrNull(id)

    override fun findByIdempotencyKey(idempotencyKey: String): GoodsOrder? =
        goodsOrderJpaRepository.findByIdempotencyKey(idempotencyKey)

    override fun findByUserId(userId: Long, pageable: Pageable): Page<GoodsOrder> =
        goodsOrderJpaRepository.findAllByUserIdAndDeletedAtIsNull(userId, pageable)
}
