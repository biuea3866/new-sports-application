package com.sportsapp.domain.recruitment.policy

import java.math.BigDecimal
import java.time.ZonedDateTime

/**
 * 신청 마감시각 대비 현재 시각(내부 해결, no-time-parameter 준수)으로
 * 취소 수수료율(0.00~1.00)을 계산하는 전략.
 */
interface CancellationPolicy {
    fun feeRateFor(applicationDeadline: ZonedDateTime): BigDecimal
}
