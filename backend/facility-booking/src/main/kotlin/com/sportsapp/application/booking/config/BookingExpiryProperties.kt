package com.sportsapp.application.booking.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * W1-11c booking PENDING 예약 만료 스위퍼 설정.
 *
 * `application.yml`에 키를 추가하지 않는다 — 아래 기본값이 코드 상 SSOT이며,
 * env(`BOOKING_EXPIRY_*`, relaxed binding)로만 재정의한다.
 * `enabled=false`가 롤백 지점이다(상태 전이·스키마 변경 0건 — EXPIRED는 기존 enum 값).
 */
@ConfigurationProperties(prefix = "booking.expiry")
data class BookingExpiryProperties(
    val enabled: Boolean = true,
    val ttlMinutes: Long = 15,
    val chunkSize: Int = 100,
    val maxChunksPerRun: Int = 20,
)
