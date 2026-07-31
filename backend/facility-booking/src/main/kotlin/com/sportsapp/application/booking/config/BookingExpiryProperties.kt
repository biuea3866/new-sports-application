package com.sportsapp.application.booking.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * W1-11c booking PENDING 예약 만료 스위퍼 튜닝 값.
 *
 * `application.yml`에 키를 추가하지 않는다 — 아래 기본값이 코드 상 SSOT이며,
 * env(`BOOKING_EXPIRY_*`, relaxed binding)로만 재정의한다.
 *
 * on/off 킬 스위치는 이 properties가 아니라 `BookingFeatureFlagKeys.EXPIRY_ENABLED`
 * (`FeatureFlagEvaluator` 런타임 조회, `IsBookingExpiryEnabledUseCase`)로 관리한다 —
 * `@ConfigurationProperties`는 부팅 시 고정 바인딩이라 값을 바꿔도 재기동 전에는 반영되지
 * 않으므로(no-conditional-on-property 취지), "플래그 OFF로 즉시 비활성화"가 성립하지 않는다.
 * 여기 남은 값(ttlMinutes/chunkSize/maxChunksPerRun)은 순수 튜닝 값이라 재기동 전제가 무방하다.
 */
@ConfigurationProperties(prefix = "booking.expiry")
data class BookingExpiryProperties(
    val ttlMinutes: Long = 15,
    val chunkSize: Int = 100,
    val maxChunksPerRun: Int = 20,
)
