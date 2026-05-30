package com.sportsapp.infrastructure.payment

import com.sportsapp.domain.payment.PaymentGateway
import com.sportsapp.domain.payment.PaymentGatewayException
import com.sportsapp.domain.payment.PaymentGatewayResult
import com.sportsapp.domain.payment.PaymentRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.ZonedDateTime
import java.util.UUID

@Component
class MockPaymentGateway(
    @Value("\${app.payment.mock.success-rate:1.0}") private val successRate: Double,
) : PaymentGateway {

    override fun requestPayment(request: PaymentRequest): PaymentGatewayResult {
        require(request.amount > java.math.BigDecimal.ZERO) {
            "Payment amount must be positive, but was ${request.amount}"
        }
        Thread.sleep(MOCK_DELAY_MS)
        if (Math.random() >= successRate) {
            throw PaymentGatewayException("MockPaymentGateway: payment failed (success-rate=$successRate)")
        }
        return PaymentGatewayResult(
            pgTransactionId = UUID.randomUUID().toString(),
            provider = "mock",
            approvedAt = ZonedDateTime.now(),
        )
    }

    companion object {
        private const val MOCK_DELAY_MS = 50L
    }
}
