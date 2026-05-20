package com.sportsapp.application.goods

import com.sportsapp.domain.goods.GoodsDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreateGoodsOrderUseCase(
    private val goodsDomainService: GoodsDomainService,
) {
    @Transactional
    fun execute(command: CreateGoodsOrderCommand): Long {
        val order = goodsDomainService.createPendingOrder(command.userId, command.items)
        return order.id
    }
}
