package com.sportsapp.application.payment.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 만료 스위퍼(W1-11a~d) 공통 "만료 금지 가드" 활동 창(activeWindow) 설정.
 *
 * PENDING/READY 결제가 이 분(minutes) 이내에 갱신됐으면 "사용자가 지금 결제 진행 중"으로
 * 보아 만료 금지 대상에 포함한다([com.sportsapp.domain.payment.service.PaymentExpiryGuard]
 * 참고). **각 주문 컨텍스트(booking 등)의 만료 TTL보다 반드시 짧아야 한다** — 같거나 길면
 * 모든 PENDING 주문이 항상 만료 금지로 판정돼 스위퍼가 무력화된다(예: booking TTL 15분,
 * 이 값 5분).
 *
 * `application.yml`에 키를 추가하지 않는다 — 아래 기본값이 코드 상 SSOT이며, env
 * (`PAYMENT_EXPIRY_GUARD_ACTIVE_WINDOW_MINUTES`, relaxed binding)로만 재정의한다.
 * booking(W1-11c) 외 goods/ticketing/recruitment 만료 스위퍼(W1-11a/b/d)도 이 properties
 * 하나를 공유해 활동 창 값의 단일 기준을 유지한다.
 */
@ConfigurationProperties(prefix = "payment.expiry-guard")
data class PaymentExpiryGuardProperties(
    val activeWindowMinutes: Long = 5,
)
