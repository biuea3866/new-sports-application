package com.sportsapp.domain.booking.dto

import java.time.ZonedDateTime

/**
 * W1-11c 만료 스위퍼 후보 조회 결과 — 예약 id와 생성 시각(createdAt)을 함께 담는다.
 *
 * 4차 재설계에서 payment의 live(READY 이상) 판정과 결합해 "느린 TTL(readyTtlMinutes)을
 * 지났는가"를 판단하려면 후보의 createdAt이 필요하다 — id만으로는 판단할 수 없어
 * [com.sportsapp.domain.booking.repository.BookingRepository.findPendingCreatedBefore]가
 * id 목록이 아니라 이 값 객체 목록을 반환하도록 확장했다.
 */
data class BookingExpiryCandidate(
    val bookingId: Long,
    val createdAt: ZonedDateTime,
)
