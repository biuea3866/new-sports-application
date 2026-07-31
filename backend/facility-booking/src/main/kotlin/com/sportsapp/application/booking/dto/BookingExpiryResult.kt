package com.sportsapp.application.booking.dto

/**
 * W1-11c 만료 스위퍼 1회 실행(또는 청크 1건) 결과 — 만료 건수·건너뛴 건수를 담는다.
 *
 * [skippedSettledCount]를 [skippedCount]와 분리한 이유: settled(결제 완료)로 건너뛴 건은
 * 웹훅 유실·컨슈머 다운으로 "돈은 받았는데 예약이 여전히 PENDING"인 **환불 판단이 필요한
 * 이상 신호**다. live(결제 진행 중)로 건너뛴 정상 흐름과 뭉뚱그리면 경보가 불가능하다
 * ([com.sportsapp.presentation.booking.scheduler.BookingExpiryScheduler]가
 * `booking_expiry_skipped_settled_total`로 별도 계측한다).
 */
data class BookingExpiryResult(
    val expiredCount: Int,
    val skippedCount: Int,
    val skippedSettledCount: Int = 0,
) {
    operator fun plus(other: BookingExpiryResult): BookingExpiryResult = BookingExpiryResult(
        expiredCount = expiredCount + other.expiredCount,
        skippedCount = skippedCount + other.skippedCount,
        skippedSettledCount = skippedSettledCount + other.skippedSettledCount,
    )

    companion object {
        fun empty(): BookingExpiryResult = BookingExpiryResult(expiredCount = 0, skippedCount = 0, skippedSettledCount = 0)
    }
}
