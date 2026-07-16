package com.sportsapp.infrastructure.payment.gateway

import com.sportsapp.domain.payment.exception.PaymentGatewayException
import com.sportsapp.domain.payment.gateway.PaymentGateway
import com.sportsapp.domain.payment.gateway.PgPrepareRequest
import com.sportsapp.domain.payment.gateway.PgPrepareResult
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.UUID
import kotlin.random.Random

@Component
class MockPaymentGateway(
    // 서버 호출 없이 자체 발급만 하지만, checkoutUrl은 브라우저가 접속하므로 공개 base-url을 쓴다
    // (MockPgGatewayImpl과 동일 원칙 — app.payment.pg.public-base-url).
    @Value("\${app.payment.pg.public-base-url:http://localhost:9090}") private val pgBaseUrl: String,
    @Value("\${app.payment.mock.success-rate:1.0}") private val successRate: Double,
) : PaymentGateway {

    override fun prepare(request: PgPrepareRequest): PgPrepareResult {
        require(request.amount > java.math.BigDecimal.ZERO) {
            "Payment amount must be positive, but was ${request.amount}"
        }
        if (Random.nextDouble() >= successRate) {
            throw PaymentGatewayException("Mock PG failure (success-rate=$successRate)")
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
