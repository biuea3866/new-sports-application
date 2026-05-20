package com.sportsapp.application.goods

import com.sportsapp.domain.goods.GoodsDomainService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListMyGoodsOrdersUseCase(
    private val goodsDomainService: GoodsDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(userId: Long, pageable: Pageable): Page<GoodsOrderSummaryResponse> =
        goodsDomainService.listMyOrders(userId, pageable).map { GoodsOrderSummaryResponse.of(it) }
}
