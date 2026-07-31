package com.sportsapp.application.booking.dto

/**
 * W1-11c 만료 스위퍼 1회 실행(또는 청크 1건) 결과 — 만료 건수·건너뛴(결제 성공) 건수.
 */
data class BookingExpiryResult(
    val expiredCount: Int,
    val skippedCount: Int,
) {
    operator fun plus(other: BookingExpiryResult): BookingExpiryResult = BookingExpiryResult(
        expiredCount = expiredCount + other.expiredCount,
        skippedCount = skippedCount + other.skippedCount,
    )

    companion object {
        fun empty(): BookingExpiryResult = BookingExpiryResult(expiredCount = 0, skippedCount = 0)
    }
}
