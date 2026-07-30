package com.sportsapp.application.goods.usecase

import com.sportsapp.domain.goods.dto.GoodsOrderDetail
import com.sportsapp.domain.goods.service.GoodsDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetGoodsOrderUseCase(
    private val goodsDomainService: GoodsDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(userId: Long, orderId: Long): GoodsOrderDetail =
        goodsDomainService.getOrder(userId, orderId)
}
