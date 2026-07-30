package com.sportsapp.application.payment.usecase

import com.sportsapp.application.payment.dto.PaymentCriteria
import com.sportsapp.application.payment.dto.PaymentResponse
import com.sportsapp.domain.payment.service.PaymentDomainService
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
