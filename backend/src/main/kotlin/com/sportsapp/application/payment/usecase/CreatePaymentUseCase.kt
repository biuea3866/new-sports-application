package com.sportsapp.application.payment.usecase

import com.sportsapp.application.payment.dto.CreatePaymentCommand
import com.sportsapp.application.payment.dto.PaymentResponse
import com.sportsapp.domain.payment.service.PaymentDomainService
import com.sportsapp.domain.payment.dto.PgInitiateCommand
import org.springframework.stereotype.Service

@Service
class CreatePaymentUseCase(
    private val paymentDomainService: PaymentDomainService,
) {
    fun execute(command: CreatePaymentCommand): PaymentResponse {
        val paymentId = paymentDomainService.createPending(
            userId = command.userId,
            idempotencyKey = command.idempotencyKey,
            orderType = command.orderType,
            orderId = command.orderId,
            method = command.method,
            amount = command.amount,
            currency = command.currency,
        )
        paymentDomainService.initiatePg(
            PgInitiateCommand(
                paymentId = paymentId,
                method = command.method,
                idempotencyKey = command.idempotencyKey,
                userId = command.userId,
                orderType = command.orderType,
                orderId = command.orderId,
                amount = command.amount,
                currency = command.currency,
                itemName = "${command.orderType} #${command.orderId}",
                returnUrl = "",
                failUrl = "",
            )
        )
        val payment = paymentDomainService.getPayment(userId = command.userId, paymentId = paymentId)
        return PaymentResponse.of(payment)
    }
}
