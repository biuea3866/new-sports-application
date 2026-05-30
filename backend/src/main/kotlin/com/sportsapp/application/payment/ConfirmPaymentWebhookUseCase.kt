package com.sportsapp.application.payment

import com.sportsapp.domain.payment.PaymentDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ConfirmPaymentWebhookUseCase(
    private val paymentDomainService: PaymentDomainService,
) {
    @Transactional
    fun execute(command: ConfirmPaymentWebhookCommand) {
        paymentDomainService.confirmWebhook(
            tid = command.tid,
            eventType = command.eventType,
        )
    }
}
