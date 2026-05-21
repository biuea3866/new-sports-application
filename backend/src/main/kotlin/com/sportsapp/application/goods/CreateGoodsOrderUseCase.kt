package com.sportsapp.application.goods

import com.sportsapp.domain.goods.CartDomainService
import com.sportsapp.domain.goods.GoodsDomainService
import com.sportsapp.domain.payment.OrderType
import com.sportsapp.domain.payment.Payment
import com.sportsapp.domain.payment.PaymentDomainService
import com.sportsapp.domain.payment.PaymentStatus
import org.springframework.stereotype.Service

@Service
class CreateGoodsOrderUseCase(
    private val goodsDomainService: GoodsDomainService,
    private val paymentDomainService: PaymentDomainService,
    private val cartDomainService: CartDomainService,
) {
    fun execute(command: CreateGoodsOrderCommand): GoodsOrderResponse {
        val order = goodsDomainService.createPendingOrder(command.userId, command.items)
        val payment = paymentDomainService.create(
            userId = command.userId,
            idempotencyKey = command.idempotencyKey,
            orderType = OrderType.GOODS,
            orderId = order.id,
            method = command.method,
            amount = order.totalAmount,
            currency = "KRW",
        )
        processPaymentResult(order.id, payment, command)
        return GoodsOrderResponse.ofCreated(
            OrderWithPayment(
                orderId = order.id,
                paymentId = payment.id,
                paymentStatus = payment.status,
                totalAmount = order.totalAmount,
            )
        )
    }

    private fun processPaymentResult(orderId: Long, payment: Payment, command: CreateGoodsOrderCommand) {
        if (payment.status == PaymentStatus.FAILED) {
            goodsDomainService.cancelPendingOrder(orderId)
            return
        }
        goodsDomainService.markPaid(orderId, payment.id)
        if (command.fromCart) cartDomainService.clearCart(command.userId)
    }
}
