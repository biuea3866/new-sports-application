package com.sportsapp.domain.recruitment.dto

import java.time.ZonedDateTime

/**
 * W1-11d 만료 스위퍼 후보 조회 결과 — 신청 id와 생성 시각(createdAt)을 함께 담는다.
 *
 * `facility-booking`(W1-11c) `BookingExpiryCandidate`와 동일한 이유로 createdAt을 포함한다 —
 * [com.sportsapp.domain.recruitment.service.RecruitmentDomainService.filterExpirable]이
 * [com.sportsapp.domain.common.payment.OrderPaymentLiveness.allowsExpiry]의 `orderCreatedAt`
 * 인자로 사용한다.
 */
data class ApplicationExpiryCandidate(
    val applicationId: Long,
    val createdAt: ZonedDateTime,
)
