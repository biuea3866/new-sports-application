package com.sportsapp.application.payment.usecase

import com.sportsapp.application.payment.dto.PaymentResponse
import com.sportsapp.domain.payment.service.PaymentDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetPaymentUseCase(
    private val paymentDomainService: PaymentDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(userId: Long, paymentId: Long): PaymentResponse {
        val payment = paymentDomainService.getPayment(userId, paymentId)
        return PaymentResponse.of(payment)
    }
}
