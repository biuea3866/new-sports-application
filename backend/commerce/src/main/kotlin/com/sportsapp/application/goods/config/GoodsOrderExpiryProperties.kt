package com.sportsapp.application.goods.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * W1-11a goods PENDING 주문 만료 스위퍼 튜닝 값.
 *
 * `application.yml`에 키를 추가하지 않는다 — 아래 기본값이 코드 상 SSOT이며,
 * env(`GOODS_EXPIRY_*`, relaxed binding)로만 재정의한다(yml 소유권은 W1-03 — 같은 wave
 * 파일 충돌 방지).
 *
 * on/off 킬 스위치는 이 properties가 아니라 `GoodsFeatureFlagKeys.EXPIRY_ENABLED`
 * (`FeatureFlagEvaluator` 런타임 조회, `IsGoodsOrderExpiryEnabledUseCase`)로 관리한다 —
 * `@ConfigurationProperties`는 부팅 시 고정 바인딩이라 값을 바꿔도 재기동 전에는 반영되지
 * 않으므로(no-conditional-on-property 취지), "플래그 OFF로 즉시 비활성화"가 성립하지
 * 않는다. 여기 남은 값(ttlMinutes/readyTtlMinutes/chunkSize/maxChunksPerRun/cycleMs)은
 * 순수 튜닝 값이라 재기동 전제가 무방하다.
 *
 * **두 TTL** — payment 상태(settled/live/attempting/none —
 * [com.sportsapp.domain.common.payment.OrderPaymentLiveness])만으로 "결제가 시작이라도
 * 됐는가"를 판정하고, 그 여부에 따라 서로 다른 TTL을 적용한다
 * (`facility-booking`(W1-11c)의 `BookingExpiryProperties`와 동일한 설계 — 활동 시각
 * (updatedAt) 기반 판정은 initiatePg가 주문 생성 요청 트랜잭션 안에서 끝나 신호가 되지
 * 못해 폐기됐다).
 * - [ttlMinutes](빠른 TTL): 결제 미시작/실패/취소/환불 등 "결제가 시작 못 했거나 종결된"
 *   주문을 만료시키는 기준. F-A(고립 주문)를 정확히 타격한다.
 * - [readyTtlMinutes](느린 TTL): live(READY 이상) 결제가 있는 주문을 만료시키는 기준.
 *   결제창에 머무는 사용자를 오만료하지 않도록 충분히 길게 잡는다.
 *
 * **불변조건**: `readyTtlMinutes`는 반드시 `ttlMinutes`보다 커야 한다 — 같거나 짧으면 live
 * 주문도 빠른 TTL에 걸려 오만료된다. env 재정의로도 이 조건이 깨지면 부팅을 실패시켜
 * (`require`) 가드 무력화를 재기동 즉시 드러낸다.
 */
@ConfigurationProperties(prefix = "goods.expiry")
data class GoodsOrderExpiryProperties(
    val ttlMinutes: Long = 30,
    val readyTtlMinutes: Long = 90,
    val chunkSize: Int = 100,
    val maxChunksPerRun: Int = 20,
    /**
     * **프로덕션 코드가 이 필드를 읽지 않는다** — 재리뷰 p3. `goods.expiry.cycle-ms` 값은
     * [com.sportsapp.presentation.goods.scheduler.GoodsOrderExpiryScheduler]의
     * `@Scheduled(fixedDelayString = "${'$'}{goods.expiry.cycle-ms:...}")` 애노테이션이
     * 부팅 시 자체적으로 문자열 보간해 바인딩한다(Spring이 `Environment`에서 직접 읽음).
     * 이 필드는 **같은 프로퍼티 키를 이 클래스가 소유한 문서로 남기기 위한 선언**일 뿐,
     * 코드 경로상 실제로 소비되지 않는다 — 값을 바꿔도 이 필드 자체는 아무것도 하지
     * 않지만 `@Scheduled`가 같은 키를 읽으므로 스케줄 주기는 바뀐다. 제거하면 이 키의
     * 존재·기본값이 문서화될 곳이 없어져 남겨둔다.
     */
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
         * 스케줄 주기(ms) 기본값 SSOT — [com.sportsapp.presentation.goods.scheduler.GoodsOrderExpiryScheduler]의
         * `@Scheduled(fixedDelayString)` 애노테이션이 컴파일 타임 상수로 이 값을 문자열
         * 보간해 참조한다(매직 상수가 properties 밖에 흩어지는 것 방지).
         */
        const val DEFAULT_CYCLE_MS = 300_000L
    }
}
