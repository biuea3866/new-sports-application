package com.sportsapp.application.payment.usecase

import com.sportsapp.application.payment.dto.PreparePaymentCommand
import com.sportsapp.application.payment.dto.PreparePaymentResponse
import com.sportsapp.application.payment.dto.toInitiateCommand
import com.sportsapp.domain.payment.service.PaymentDomainService
import org.springframework.stereotype.Service

@Service
class PreparePaymentUseCase(
    private val paymentDomainService: PaymentDomainService,
) {
    fun execute(command: PreparePaymentCommand): PreparePaymentResponse {
        val paymentId = paymentDomainService.createPending(
            userId = command.userId,
            idempotencyKey = command.idempotencyKey,
            orderType = command.orderType,
            orderId = command.orderId,
            method = command.method,
            amount = command.amount,
            currency = command.currency,
        )
        val pgResult = paymentDomainService.initiatePg(command.toInitiateCommand(paymentId))
        return PreparePaymentResponse(
            paymentId = pgResult.paymentId,
            checkoutUrl = pgResult.checkoutUrl ?: "",
            pgTransactionId = pgResult.pgTransactionId ?: "",
        )
    }
}
