package com.sportsapp.domain.booking.dto

import java.time.ZonedDateTime

/**
 * W1-11c 만료 스위퍼 후보 조회 결과 — 예약 id와 생성 시각(createdAt)을 함께 담는다.
 *
 * **5차 재설계 — createdAt은 느린 TTL(readyTtlMinutes)의 앵커가 아니다.** 4차 설계는 이
 * createdAt으로 "느린 TTL을 지났는가"를 판단했으나, `POST /payments/prepare`가 기존 주문에
 * 새 payment 행을 만드는 가동 중 경로라 예약 생성 시각과 결제 발급 시각이 무관해져 오만료가
 * 재발했다([com.sportsapp.domain.payment.service.PaymentLivenessClassifier] KDoc 참고).
 * 느린 TTL 판정은 payment의 발급 시각([com.sportsapp.domain.common.payment.OrderPaymentLiveness.Live.since])을
 * 앵커로 쓴다.
 *
 * **6차 재설계 — createdAt은 빠른 TTL(ttlMinutes) 갈래에서 다시 쓰인다.** 5차는 live payment가
 * 없는 후보를 조회 시점(SQL의 `createdAt < now - ttlMinutes` 조건)에 이미 검증이 끝난 것으로
 * 보고 [BookingDomainService.filterExpirable]에서 재검증하지 않았다 — 그런데
 * `PaymentDomainService.createPending`이 PENDING 행을 먼저 커밋하고 PG 왕복 동안 그 상태로
 * 머무는 창(δ) 안에 스위퍼가 돌면, "예약은 오래됐지만 방금 새로 시작된 재결제 시도"가
 * 신호 없음(None)으로 오판정돼 오만료될 수 있었다(6차 재리뷰 p1). 이제 이 createdAt은
 * PENDING 시도 시각([com.sportsapp.domain.common.payment.OrderPaymentLiveness.Attempting.since])과
 * **함께 최댓값**으로 재평가된다 — `max(candidate.createdAt, attemptSince)`가 빠른 TTL
 * 임계값을 지나야 실제로 만료된다([BookingDomainService.filterExpirable] 참고).
 */
data class BookingExpiryCandidate(
    val bookingId: Long,
    val createdAt: ZonedDateTime,
)
