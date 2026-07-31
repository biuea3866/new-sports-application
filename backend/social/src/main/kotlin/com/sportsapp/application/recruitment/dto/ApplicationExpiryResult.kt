package com.sportsapp.application.recruitment.dto

/**
 * W1-11d 만료 스위퍼 1회 실행(또는 청크 1건) 결과 — 취소 건수·건너뛴 건수를 담는다
 * (`facility-booking`(W1-11c) `BookingExpiryResult`와 동일한 이유).
 *
 * [skippedSettledCount]를 [skippedCount]와 분리한 이유: settled(결제 완료)로 건너뛴 건은
 * 웹훅 유실·컨슈머 다운으로 "돈은 받았는데 신청이 여전히 PENDING"인 **환불 판단이 필요한
 * 이상 신호**다. live(결제 진행 중)로 건너뛴 정상 흐름과 뭉뚱그리면 경보가 불가능하다.
 *
 * [contendedCount]: `filterExpirable`이 만료 대상으로 판정했으나
 * [com.sportsapp.domain.recruitment.repository.ApplicationRepository.tryExpire] CAS(WHERE
 * status='PENDING')가 실패한 건 — 청크 처리 도중 다른 트랜잭션(webhook 확정)이 먼저
 * CONFIRMED로 전이시켜 경합에서 진 경우다. 별도 카운터로 분리해 계측한다.
 */
data class ApplicationExpiryResult(
    val expiredCount: Int,
    val skippedCount: Int,
    val skippedSettledCount: Int = 0,
    val contendedCount: Int = 0,
) {
    operator fun plus(other: ApplicationExpiryResult): ApplicationExpiryResult = ApplicationExpiryResult(
        expiredCount = expiredCount + other.expiredCount,
        skippedCount = skippedCount + other.skippedCount,
        skippedSettledCount = skippedSettledCount + other.skippedSettledCount,
        contendedCount = contendedCount + other.contendedCount,
    )

    companion object {
        fun empty(): ApplicationExpiryResult =
            ApplicationExpiryResult(expiredCount = 0, skippedCount = 0, skippedSettledCount = 0, contendedCount = 0)
    }
}
