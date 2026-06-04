package com.sportsapp.infrastructure.payment.gateway

import com.sportsapp.domain.payment.gateway.PaymentGateway
import com.sportsapp.domain.payment.gateway.PgPrepareRequest
import com.sportsapp.domain.payment.gateway.PgPrepareResult
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class MockPaymentGateway(
    @Value("\${app.payment.mock.pg-base-url:http://localhost:9090}") private val pgBaseUrl: String,
) : PaymentGateway {

    override fun prepare(request: PgPrepareRequest): PgPrepareResult {
        require(request.amount > java.math.BigDecimal.ZERO) {
            "Payment amount must be positive, but was ${request.amount}"
        }
        val tid = "MOCK_${request.provider.uppercase()}_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val checkoutUrl = "$pgBaseUrl/pg/${request.provider}/checkout?tid=$tid"
        return PgPrepareResult(
            tid = tid,
            provider = request.provider,
            checkoutUrl = checkoutUrl,
        )
    }
}
