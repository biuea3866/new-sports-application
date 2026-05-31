package com.sportsapp.infrastructure.payment

import com.sportsapp.domain.payment.OrderConfirmationGateway
import com.sportsapp.domain.payment.OrderType
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * OrderConfirmationGateway stub 구현체.
 *
 * BE-02(주문 확정 게이트웨이 구현) 전까지 사용하는 로깅 전용 stub.
 * @Profile("!prod") 로 운영 환경에서는 실 구현체로 교체됩니다.
 */
@Component
@Profile("!prod")
class StubOrderConfirmationGateway : OrderConfirmationGateway {

    private val log = LoggerFactory.getLogger(StubOrderConfirmationGateway::class.java)

    override fun confirm(orderType: OrderType, orderId: Long, paymentId: Long) {
        log.info("StubOrderConfirmationGateway.confirm: orderType={} orderId={} paymentId={}", orderType, orderId, paymentId)
    }
}
