package com.sportsapp.application.goods.usecase

import com.sportsapp.domain.goods.entity.GoodsOrder
import com.sportsapp.domain.goods.service.GoodsDomainService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListMyGoodsOrdersUseCase(
    private val goodsDomainService: GoodsDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(userId: Long, pageable: Pageable): Page<GoodsOrder> =
        goodsDomainService.listMyOrders(userId, pageable)
}
