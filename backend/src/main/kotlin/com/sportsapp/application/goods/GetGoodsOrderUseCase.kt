package com.sportsapp.application.goods

import com.sportsapp.domain.goods.GoodsDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetGoodsOrderUseCase(
    private val goodsDomainService: GoodsDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(userId: Long, orderId: Long): GoodsOrderResponse {
        val (order, items) = goodsDomainService.getOrder(userId, orderId)
        return GoodsOrderResponse.of(order, items)
    }
}
