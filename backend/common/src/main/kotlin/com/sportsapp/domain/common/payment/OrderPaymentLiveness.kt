package com.sportsapp.domain.common.payment

import java.time.ZonedDateTime

/**
 * 만료 스위퍼(W1-11a~d 공통 만료 금지 가드)가 소비하는 "주문 1건의 결제 생존 판정" 공유 커널.
 *
 * payment(코어)가 계산해 값으로 반환하고, booking·goods·ticketing·recruitment(4개 주문
 * 도메인)가 각자 own TTL 정책으로 소비한다. domain 레이어는 domain.common만 import 가능하므로
 * (`SharedKernelPurityRulesTest` R4), payment·4개 주문 도메인이 모두 합법적으로 참조 가능한
 * 유일 위치가 domain.common이다 — [com.sportsapp.domain.common.order.OrderType]과 동일한
 * "공유 커널 승격" 패턴이다.
 *
 * **6차 재설계 (p3-3 — 계약을 오용 불가능한 형태로)**: 이전에는
 * `PaymentLivenessQueryResult`가 `liveSince: Map<Long, ZonedDateTime>` +
 * `settledOrderIds: Set<Long>`를 나란히 반환했고, "settled를 먼저 걸러야 한다"는 불변식이
 * KDoc에만 있었다. 소비 도메인이 각자 이 2단계 판정을 재구현해야 했으므로, 한 곳만 순서를
 * 놓치면(예: settled 체크를 빼먹고 liveSince만 보면) COMPLETED 주문이 만료 대상으로 잘못
 * 판정돼 "돈 받고 서비스 없음"이 재발할 수 있었다. sealed `when` 전수 분기로 바꾸면
 * [Settled] 분기를 빼먹는 순간 컴파일이 깨진다 — 판정 순서를 타입이 강제한다.
 *
 * - [Settled]: COMPLETED — 돈을 받았다. TTL 앵커 필드가 아예 없다 — 시간값을 실수로
 *   끼워 넣어 만료 판정에 쓸 수 없는 구조다(절대 만료 금지).
 * - [Live]: READY 또는 COMPLETED 중 발급 시각([since])이 존재하는 상태 — 느린 TTL 앵커.
 *   COMPLETED는 [Settled]로도 계산되므로(같은 주문이 두 변이 후보에 모두 해당해도 classify는
 *   [Settled] 하나만 반환한다), 소비측이 sealed `when`을 쓰면 [Settled] 분기가 구조적으로
 *   먼저 잡혀 [Live]로는 도달하지 않는다.
 * - [Attempting]: PENDING(PG 왕복 대기 중, 아직 checkoutUrl 미발급) — 빠른 TTL이나 앵커는
 *   이 시도 시작 시각([since])과 주문 생성 시각의 **최댓값**이어야 한다(6차 재설계 — p1.
 *   `createPending`이 PENDING 행을 먼저 자기 트랜잭션으로 커밋하고, 그 뒤 PG 왕복(δ) 동안
 *   그 행은 PENDING으로 머문다 — 주문 생성 시각만 보고 만료를 허용하면 δ 안에 스위퍼가
 *   돌 때 "방금 시작된 재결제 시도"를 오만료시킨다). 최댓값 계산은 소비 도메인이 한다 —
 *   payment는 주문 생성 시각을 모른다.
 * - [None]: 결제 시도 이력이 없거나 전부 종결(FAILED/CANCELLED/REFUNDED)만 있음 — 빠른 TTL,
 *   앵커는 소비 도메인이 소유한 주문 생성 시각을 그대로 쓴다.
 *
 * 시간 인자를 받지 않는다 — TTL(분값) 자체는 이 타입이 갖지 않는다. "슬롯을 얼마나 붙잡아
 * 둘 것인가"는 소비 도메인의 정책이므로 [Live]/[Attempting]은 발급·시도 시각([since])만
 * 반환하고, 몇 분을 허용할지는 소비 도메인(예: booking의 `BookingExpiryProperties`)이
 * 결정한다 — "payment는 사실만 답한다" 원칙과 충돌하지 않는다.
 */
sealed interface OrderPaymentLiveness {
    data object Settled : OrderPaymentLiveness
    data class Live(val since: ZonedDateTime) : OrderPaymentLiveness
    data class Attempting(val since: ZonedDateTime) : OrderPaymentLiveness
    data object None : OrderPaymentLiveness
}
