package com.sportsapp.application.ticketing.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * W1-11b ticketing PENDING 주문 만료 스위퍼 튜닝 값 — booking(W1-11c)의
 * [com.sportsapp.application.booking.config.BookingExpiryProperties]와 동일한 구조를 그대로
 * 따른다.
 *
 * `application.yml`에 키를 추가하지 않는다 — 아래 기본값이 코드 상 SSOT이며, env
 * (`TICKETING_EXPIRY_*`, relaxed binding)로만 재정의한다.
 *
 * on/off 킬 스위치는 이 properties가 아니라 `TicketingFeatureFlagKeys.EXPIRY_ENABLED`
 * (`FeatureFlagEvaluator` 런타임 조회, `IsTicketOrderExpiryEnabledUseCase`)로 관리한다 —
 * `@ConfigurationProperties`는 부팅 시 고정 바인딩이라 값을 바꿔도 재기동 전에는 반영되지
 * 않으므로(no-conditional-on-property 취지), "플래그 OFF로 즉시 비활성화"가 성립하지 않는다.
 *
 * **두 TTL**: payment 상태만으로 "결제가 시작이라도 됐는가"(live)를 판정하고, live 여부에
 * 따라 서로 다른 TTL을 적용한다([com.sportsapp.domain.common.payment.OrderPaymentLiveness]
 * 참고).
 * - [ttlMinutes](빠른 TTL): 결제 미시작/실패/취소/환불 등 "결제가 시작 못 했거나 종결된"
 *   주문을 만료시키는 기준. F-A(고립 주문)를 정확히 타격한다.
 * - [readyTtlMinutes](느린 TTL): live(READY 이상) 결제가 있는 주문을 만료시키는 기준. 결제창에
 *   머무는 사용자를 오만료하지 않도록 충분히 길게 잡는다.
 *
 * **불변조건**: `readyTtlMinutes`는 반드시 `ttlMinutes`보다 커야 한다 — 같거나 짧으면 live
 * 주문도 빠른 TTL에 걸려 오만료된다. env 재정의로도 이 조건이 깨지면 부팅을 실패시켜
 * (`require`) 가드 무력화를 재기동 즉시 드러낸다.
 */
@ConfigurationProperties(prefix = "ticketing.expiry")
data class TicketOrderExpiryProperties(
    val ttlMinutes: Long = 15,
    val readyTtlMinutes: Long = 60,
    val chunkSize: Int = 100,
    val maxChunksPerRun: Int = 20,
    val cycleMs: Long = DEFAULT_CYCLE_MS,
) {
    init {
        require(readyTtlMinutes > ttlMinutes) {
            "readyTtlMinutes($readyTtlMinutes)는 ttlMinutes($ttlMinutes)보다 커야 한다 — " +
                "그렇지 않으면 결제 진행 중(live)인 주문이 빠른 TTL에 의해 오만료된다"
        }
    }

    companion object {
        /**
         * 스케줄 주기(ms) 기본값 SSOT — [com.sportsapp.presentation.ticketing.scheduler.TicketOrderExpiryScheduler]의
         * `@Scheduled(fixedDelayString)` 애노테이션이 컴파일 타임 상수로 이 값을 문자열 보간해
         * 참조한다.
         */
        const val DEFAULT_CYCLE_MS = 300_000L
    }
}
