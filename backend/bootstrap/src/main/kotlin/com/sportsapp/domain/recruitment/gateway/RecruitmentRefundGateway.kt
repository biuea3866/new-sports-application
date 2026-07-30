package com.sportsapp.domain.recruitment.gateway

import java.math.BigDecimal

/**
 * Recruitment 신청 취소·모집 취소에 따른 환불 요청 추상화.
 * booking 의 PaymentRefundGateway 와 별개 interface (R1 — 도메인 컨텍스트 간 교차 참조 금지).
 */
interface RecruitmentRefundGateway {
    fun requestRefund(paymentId: Long, amount: BigDecimal, reason: String)
}
