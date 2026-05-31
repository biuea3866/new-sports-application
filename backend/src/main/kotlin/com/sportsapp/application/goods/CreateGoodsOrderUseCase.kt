package com.sportsapp.application.goods

import com.sportsapp.domain.common.Currency
import com.sportsapp.domain.goods.GoodsDomainService
import com.sportsapp.domain.goods.GoodsOrder
import com.sportsapp.domain.goods.GoodsOrderStatus
import com.sportsapp.domain.payment.OrderType
import com.sportsapp.domain.payment.PaymentDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreateGoodsOrderUseCase(
    private val goodsDomainService: GoodsDomainService,
    private val paymentDomainService: PaymentDomainService,
) {
    @Transactional
    fun execute(command: CreateGoodsOrderCommand): GoodsOrderResponse {
        val order = goodsDomainService.createPendingOrder(command.userId, command.items, command.idempotencyKey)
        if (order.status != GoodsOrderStatus.PENDING) return buildIdempotentResponse(order)
        val payment = paymentDomainService.create(
            userId = command.userId,
            idempotencyKey = command.idempotencyKey,
            orderType = OrderType.GOODS,
            orderId = order.id,
            method = command.method,
            amount = order.totalAmount,
            currency = Currency.KRW.code,
        )
        return GoodsOrderResponse.ofCreated(
            OrderWithPayment(
                orderId = order.id,
                paymentId = payment.id,
                paymentStatus = payment.status,
                totalAmount = order.totalAmount,
            )
        )
    }

    private fun buildIdempotentResponse(order: GoodsOrder) = GoodsOrderResponse.ofCreated(
        OrderWithPayment(
            orderId = order.id,
            paymentId = order.paymentId,
            paymentStatus = null,
            totalAmount = order.totalAmount,
        )
    )
}
