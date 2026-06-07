package com.sportsapp.application.goods.usecase

import com.sportsapp.application.goods.dto.CreateGoodsOrderCommand
import com.sportsapp.application.goods.dto.OrderWithPayment
import com.sportsapp.domain.common.Currency
import com.sportsapp.domain.goods.entity.GoodsOrder
import com.sportsapp.domain.goods.entity.GoodsOrderStatus
import com.sportsapp.domain.goods.service.GoodsDomainService
import com.sportsapp.domain.payment.dto.PgInitiateCommand
import com.sportsapp.domain.payment.service.PaymentDomainService
import com.sportsapp.domain.payment.vo.OrderType
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

@Service
class CreateGoodsOrderUseCase(
    private val goodsDomainService: GoodsDomainService,
    private val paymentDomainService: PaymentDomainService,
    private val transactionTemplate: TransactionTemplate,
) {
    fun execute(command: CreateGoodsOrderCommand): OrderWithPayment {
        val (order, paymentId) = persistOrderAndPendingPayment(command)
        if (order.status != GoodsOrderStatus.PENDING) return buildIdempotentResult(order)
        val pgResult = paymentDomainService.initiatePg(
            PgInitiateCommand(
                paymentId = paymentId,
                method = command.method,
                idempotencyKey = command.idempotencyKey,
                userId = command.userId,
                orderType = OrderType.GOODS,
                orderId = order.id,
                amount = order.totalAmount,
                currency = Currency.KRW.code,
                itemName = "GOODS #${order.id}",
                returnUrl = "",
                failUrl = "",
            )
        )
        return OrderWithPayment(
            orderId = order.id,
            paymentId = pgResult.paymentId,
            paymentStatus = pgResult.status,
            totalAmount = order.totalAmount,
        )
    }

    private fun persistOrderAndPendingPayment(command: CreateGoodsOrderCommand): Pair<GoodsOrder, Long> {
        return transactionTemplate.execute {
            val order = goodsDomainService.createPendingOrder(command.userId, command.items, command.idempotencyKey)
            if (order.status != GoodsOrderStatus.PENDING) return@execute order to (order.paymentId ?: 0L)
            val paymentId = paymentDomainService.createPending(
                userId = command.userId,
                idempotencyKey = command.idempotencyKey,
                orderType = OrderType.GOODS,
                orderId = order.id,
                method = command.method,
                amount = order.totalAmount,
                currency = Currency.KRW.code,
            )
            order to paymentId
        } ?: throw IllegalStateException("Transaction returned null")
    }

    private fun buildIdempotentResult(order: GoodsOrder) = OrderWithPayment(
        orderId = order.id,
        paymentId = order.paymentId,
        paymentStatus = null,
        totalAmount = order.totalAmount,
    )
}
