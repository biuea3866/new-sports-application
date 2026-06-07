package com.sportsapp.application.goods.usecase

import com.sportsapp.domain.goods.entity.GoodsOrder
import com.sportsapp.domain.goods.entity.GoodsOrderItem
import com.sportsapp.domain.goods.service.GoodsDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetGoodsOrderUseCase(
    private val goodsDomainService: GoodsDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(userId: Long, orderId: Long): Pair<GoodsOrder, List<GoodsOrderItem>> =
        goodsDomainService.getOrder(userId, orderId)
}
