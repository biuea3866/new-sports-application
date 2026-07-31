package com.sportsapp.application.ticketing.dto

/**
 * W1-11b 만료 스위퍼 1회 실행(또는 청크 1건) 결과 — 만료 건수·건너뛴 건수를 담는다.
 * booking(W1-11c)의 [com.sportsapp.application.booking.dto.BookingExpiryResult]와 동일한
 * 구조 — [skippedSettledCount]는 환불 판단이 필요한 이상 신호(웹훅 유실 등)를 정상 건너뜀과
 * 분리해 계측하고, [contendedCount]는 CAS(tryExpire) 경합 패배 건수를 별도 계측한다.
 */
data class TicketOrderExpiryResult(
    val expiredCount: Int,
    val skippedCount: Int,
    val skippedSettledCount: Int = 0,
    val contendedCount: Int = 0,
) {
    operator fun plus(other: TicketOrderExpiryResult): TicketOrderExpiryResult = TicketOrderExpiryResult(
        expiredCount = expiredCount + other.expiredCount,
        skippedCount = skippedCount + other.skippedCount,
        skippedSettledCount = skippedSettledCount + other.skippedSettledCount,
        contendedCount = contendedCount + other.contendedCount,
    )

    companion object {
        fun empty(): TicketOrderExpiryResult =
            TicketOrderExpiryResult(expiredCount = 0, skippedCount = 0, skippedSettledCount = 0, contendedCount = 0)
    }
}
