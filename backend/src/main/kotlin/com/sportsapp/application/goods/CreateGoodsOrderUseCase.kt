package com.sportsapp.application.goods

import com.sportsapp.domain.goods.CartDomainService
import com.sportsapp.domain.goods.GoodsDomainService
import com.sportsapp.application.goods.OrderWithPayment
import com.sportsapp.domain.payment.OrderType
import com.sportsapp.domain.payment.PaymentDomainService
import org.springframework.stereotype.Service

@Service
class CreateGoodsOrderUseCase(
    private val goodsDomainService: GoodsDomainService,
    private val paymentDomainService: PaymentDomainService,
    private val cartDomainService: CartDomainService,
) {
    fun execute(command: CreateGoodsOrderCommand): GoodsOrderResponse {
        val order = goodsDomainService.createPendingOrder(command.userId, command.items)

        val payment = runCatching {
            paymentDomainService.create(
                userId = command.userId,
                idempotencyKey = command.idempotencyKey,
                orderType = OrderType.GOODS,
                orderId = order.id,
                method = command.method,
                amount = order.totalAmount,
                currency = "KRW",
            )
        }.getOrElse {
            goodsDomainService.cancelPendingOrder(order.id)
            throw it
        }

        if (command.fromCart) cartDomainService.clearCart(command.userId)

        return GoodsOrderResponse.ofCreated(
            OrderWithPayment(
                orderId = order.id,
                paymentId = payment.id,
                paymentStatus = payment.status,
                totalAmount = order.totalAmount,
            )
        )
    }
}
