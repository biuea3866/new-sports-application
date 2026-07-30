package com.sportsapp.application.payment.usecase

import com.sportsapp.application.payment.dto.ConfirmPaymentWebhookCommand
import com.sportsapp.application.payment.dto.PaymentResponse
import com.sportsapp.domain.payment.dto.ConfirmWebhookResult
import com.sportsapp.domain.payment.service.PaymentDomainService
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Service

@Service
class ConfirmPaymentWebhookUseCase(
    private val paymentDomainService: PaymentDomainService,
) {
    fun execute(command: ConfirmPaymentWebhookCommand): PaymentResponse {
        val result: ConfirmWebhookResult = try {
            paymentDomainService.confirmWebhook(
                tid = command.tid,
                eventType = command.eventType,
            )
        } catch (exception: DataIntegrityViolationException) {
            paymentDomainService.findByPgTransactionIdOrThrow(command.tid)
        } catch (exception: ObjectOptimisticLockingFailureException) {
            paymentDomainService.findByPgTransactionIdOrThrow(command.tid)
        }
        return PaymentResponse.of(result)
    }
}
