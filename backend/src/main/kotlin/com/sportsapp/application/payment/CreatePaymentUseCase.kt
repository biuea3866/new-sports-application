package com.sportsapp.application.payment

import com.sportsapp.domain.payment.PaymentDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreatePaymentUseCase(
    private val paymentDomainService: PaymentDomainService,
) {
    @Transactional
    fun execute(command: CreatePaymentCommand): PaymentResponse {
        val payment = paymentDomainService.create(
            userId = command.userId,
            idempotencyKey = command.idempotencyKey,
            orderType = command.orderType,
            orderId = command.orderId,
            method = command.method,
            amount = command.amount,
            currency = command.currency,
        )
        return PaymentResponse.of(payment)
    }
}
