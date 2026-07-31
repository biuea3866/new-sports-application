package com.sportsapp.domain.booking.dto

import java.time.ZonedDateTime

/**
 * W1-11c 만료 스위퍼 후보 조회 결과 — 예약 id와 생성 시각(createdAt)을 함께 담는다.
 *
 * **5차 재설계 — createdAt은 더 이상 느린 TTL(readyTtlMinutes)의 앵커가 아니다.** 4차
 * 설계는 이 createdAt으로 "느린 TTL을 지났는가"를 판단했으나, `POST /payments/prepare`가
 * 기존 주문에 새 payment 행을 만드는 가동 중 경로라 예약 생성 시각과 결제 발급 시각이
 * 무관해져 오만료가 재발했다([com.sportsapp.domain.payment.service.PaymentLivenessClassifier]
 * KDoc 참고). 느린 TTL 판정은 이제 payment의 발급 시각
 * ([com.sportsapp.domain.payment.dto.PaymentLivenessQueryResult.liveSince])을 앵커로 쓴다.
 *
 * 이 createdAt은 (1) [com.sportsapp.domain.booking.repository.BookingRepository.findPendingCreatedBefore]가
 * 빠른 TTL(ttlMinutes) 조건을 SQL에서 이미 적용해 반환한 값(참고용 원본 시각)이고, (2)
 * live payment가 전혀 없는 후보(빠른 TTL 경로)는 조회 시점에 이미 검증이 끝났으므로
 * [com.sportsapp.domain.booking.service.BookingDomainService.filterExpirable]에서 별도로
 * 재검증하지 않는다.
 */
data class BookingExpiryCandidate(
    val bookingId: Long,
    val createdAt: ZonedDateTime,
)
