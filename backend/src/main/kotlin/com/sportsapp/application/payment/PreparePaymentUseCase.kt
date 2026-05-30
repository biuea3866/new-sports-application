package com.sportsapp.application.payment

import com.sportsapp.domain.payment.PaymentDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PreparePaymentUseCase(
    private val paymentDomainService: PaymentDomainService,
) {
    @Transactional
    fun execute(command: PreparePaymentCommand): PreparePaymentResponse {
        val payment = paymentDomainService.prepare(
            userId = command.userId,
            idempotencyKey = command.idempotencyKey,
            orderType = command.orderType,
            orderId = command.orderId,
            method = command.method,
            amount = command.amount,
            currency = command.currency,
            itemName = command.itemName,
            returnUrl = command.returnUrl,
            failUrl = command.failUrl,
        )
        return PreparePaymentResponse.of(payment)
    }
}
