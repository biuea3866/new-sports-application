package com.sportsapp.infrastructure.recruitment.gateway

import com.sportsapp.domain.recruitment.gateway.RecruitmentRefundGateway
import java.math.BigDecimal
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * RecruitmentRefundGateway stub 구현체.
 *
 * PG sandbox 확보 전까지 사용하는 항상-성공 로그 전용 stub.
 * @Profile("!prod")로 운영 환경에서는 실 PG 어댑터로 교체된다 (booking StubPaymentRefundGateway와 동형).
 */
@Component
@Profile("!prod")
class RecruitmentRefundGatewayImpl : RecruitmentRefundGateway {

    private val log = LoggerFactory.getLogger(RecruitmentRefundGatewayImpl::class.java)

    override fun requestRefund(paymentId: Long, amount: BigDecimal, reason: String) {
        log.info("stub 환불 완료 — paymentId={} amount={} reason={}", paymentId, amount, reason)
    }
}
