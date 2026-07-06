package com.sportsapp.domain.recruitment.policy

import java.math.BigDecimal
import java.time.Duration
import java.time.ZonedDateTime

/**
 * 마감 잔여기간에 따라 3단계로 취소 수수료율을 산정한다.
 * - 잔여 7일 이상: 0%
 * - 잔여 3일 초과 7일 미만: 5%
 * - 잔여 3일 이하: 10%
 *
 * 잔여기간은 하루 단위로 올림 계산한다 — 실행 시점의 미세한 시간 오차(now 평가 지연)로
 * "정확히 N일 전" 경계값이 흔들리지 않도록 하기 위함이다.
 */
class TieredCancellationPolicy : CancellationPolicy {

    override fun feeRateFor(applicationDeadline: ZonedDateTime): BigDecimal {
        val remainingDays = daysRemainingUntil(applicationDeadline)
        return when {
            remainingDays >= LONG_NOTICE_DAYS -> BigDecimal.ZERO
            remainingDays > SHORT_NOTICE_DAYS -> MID_TIER_RATE
            else -> HIGH_TIER_RATE
        }
    }

    private fun daysRemainingUntil(deadline: ZonedDateTime): Long {
        val remaining = Duration.between(ZonedDateTime.now(), deadline)
        if (remaining.isZero || remaining.isNegative) return 0
        val remainingMillis = remaining.toMillis()
        return (remainingMillis + ONE_DAY_MILLIS - 1) / ONE_DAY_MILLIS
    }

    companion object {
        private const val LONG_NOTICE_DAYS = 7L
        private const val SHORT_NOTICE_DAYS = 3L
        private const val ONE_DAY_MILLIS = 24 * 60 * 60 * 1000L
        private val MID_TIER_RATE = BigDecimal("0.05")
        private val HIGH_TIER_RATE = BigDecimal("0.10")
    }
}
