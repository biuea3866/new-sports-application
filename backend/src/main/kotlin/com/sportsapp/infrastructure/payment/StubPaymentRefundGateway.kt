package com.sportsapp.infrastructure.payment

import com.sportsapp.domain.booking.PaymentRefundGateway
import com.sportsapp.domain.booking.RefundResult
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.UUID

/**
 * PaymentRefundGateway stub 구현체.
 *
 * PG sandbox 확보(Open Issue #7) 전까지 사용하는 항상-성공 stub.
 * @Profile("!prod") 로 운영 환경에서는 실 PG 어댑터로 교체됩니다.
 * 실 PG 어댑터 위치: infrastructure/payment/TossPaymentRefundGatewayImpl.kt (후속 작업)
 */
@Component
@Profile("!prod")
class StubPaymentRefundGateway : PaymentRefundGateway {

    override fun requestRefund(paymentId: String, amount: BigDecimal, reason: String): RefundResult {
        return RefundResult(
            externalRefundId = "stub-refund-${UUID.randomUUID()}",
            refundedAmount = amount,
            message = "stub 환불 완료: $reason",
        )
    }
}
