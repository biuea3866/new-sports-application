package com.sportsapp.application.goods

import com.sportsapp.domain.goods.GoodsDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreateGoodsOrderUseCase(
    private val goodsDomainService: GoodsDomainService,
) {
    @Transactional
    fun execute(command: CreateGoodsOrderCommand): GoodsOrderResponse {
        val result = goodsDomainService.createOrderWithPayment(
            userId = command.userId,
            idempotencyKey = command.idempotencyKey,
            method = command.method,
            fromCart = command.fromCart,
            items = command.items,
        )
        return GoodsOrderResponse.ofCreated(result)
    }
}
