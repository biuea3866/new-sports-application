package com.sportsapp.domain.ticketing

/**
 * ticketing 도메인 피처 플래그 키 상수. `FeatureFlagEvaluator.isEnabled(key, context, default)`가
 * 소비하는 문자열 키를 한 곳에 모아 매직 스트링 중복을 막는다
 * (선례: [com.sportsapp.domain.booking.BookingFeatureFlagKeys]).
 *
 * 플래그는 부팅 토글(`@ConditionalOnProperty`)이 아니라 **런타임 조회**로 분기한다
 * (no-conditional-on-property) — OFF로 바꾸면 재기동 없이 다음 스케줄 주기부터 즉시
 * 비활성화된다.
 */
object TicketingFeatureFlagKeys {
    /**
     * W1-11b ticketing PENDING 주문 만료 스위퍼 운영 킬 스위치. ON(기본값)이면
     * `TicketOrderExpiryScheduler`가 매 주기 스위퍼를 실행하고, OFF로 바꾸면 재기동 없이
     * 다음 주기부터 즉시 중단된다(상태 전이·스키마 변경 0건 — 롤백 지점).
     */
    const val EXPIRY_ENABLED = "ticketing.expiry.enabled"
}
