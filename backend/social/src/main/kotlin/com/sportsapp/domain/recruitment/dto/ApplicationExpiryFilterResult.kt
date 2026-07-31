package com.sportsapp.domain.recruitment.dto

/**
 * [com.sportsapp.domain.recruitment.service.RecruitmentDomainService.filterExpirable] 판정 결과.
 *
 * `skippedSettledCount`를 `expirableIds` 제외분과 분리해 반환하는 이유: settled(결제
 * 완료)로 건너뛴 건은 **환불 판단이 필요한 이상 신호**(웹훅 유실·컨슈머 다운으로 결제는
 * 됐는데 신청이 여전히 PENDING)인 반면, live(결제 진행 중)로 건너뛴 건은 정상 흐름이다.
 * 두 사유를 뭉뚱그려 하나의 skippedCount로만 계측하면 경보가 불가능하다
 * (`facility-booking`(W1-11c) `BookingExpiryFilterResult`와 동일한 이유).
 */
data class ApplicationExpiryFilterResult(
    val expirableIds: List<Long>,
    val skippedSettledCount: Int,
)
