package com.sportsapp.application.payment

import com.sportsapp.domain.payment.PaymentDomainService
import org.springframework.data.domain.Page
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListMyPaymentsUseCase(
    private val paymentDomainService: PaymentDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(criteria: PaymentCriteria): Page<PaymentResponse> {
        return paymentDomainService.findMyPayments(
            userId = criteria.userId,
            status = criteria.status,
            paidAtFrom = criteria.paidAtFrom,
            paidAtTo = criteria.paidAtTo,
            pageable = criteria.pageable,
        ).map { PaymentResponse.of(it) }
    }
}
