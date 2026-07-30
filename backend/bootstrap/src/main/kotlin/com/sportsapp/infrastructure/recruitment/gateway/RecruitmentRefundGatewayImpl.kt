package com.sportsapp.infrastructure.recruitment.gateway

import com.sportsapp.domain.recruitment.gateway.RecruitmentRefundGateway
import java.math.BigDecimal
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * RecruitmentRefundGateway stub 구현체.
 *
 * PG sandbox 확보 전까지 사용하는 항상-성공 로그 전용 stub. RecruitmentRefundEventWorker가
 * 프로파일 제한 없이 이 빈을 주입받으므로, prod를 포함한 모든 프로파일에 등록한다 — prod 전용
 * @Profile("!prod")로 제한하면 prod 컨텍스트 기동이 실패한다. 실 PG 어댑터는 W3에서 이 stub을 대체한다.
 */
@Component
class RecruitmentRefundGatewayImpl : RecruitmentRefundGateway {

    private val log = LoggerFactory.getLogger(RecruitmentRefundGatewayImpl::class.java)

    override fun requestRefund(paymentId: Long, amount: BigDecimal, reason: String) {
        log.info("stub 환불 완료 — paymentId={} amount={} reason={}", paymentId, amount, reason)
    }
}
