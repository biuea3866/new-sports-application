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
 * **8차 재설계 (p1 — "승자 하나 고르기" 구조 자체가 반복 재발의 근본 원인)**: 6차·7차는
 * live/attempting 중 **하나만** 골라 반환했다(6차: 카테고리 우선순위, 7차: 최댓값 교차
 * 비교). 그런데 소비측(booking 등)이 두 카테고리를 **서로 다른 TTL**(느린 TTL=live,
 * 빠른 TTL=attempting)로 소비하는 이상, "하나만 고르기"는 구조적으로 방향이 뒤집힌 오만료를
 * 만들어낼 수밖에 없다 — 더 최신인 attempting이 더 이른 deadline(빠른 TTL)을 낳을 수 있어,
 * 교차 비교로 "더 최신" 쪽을 고르면 오히려 **보호 창이 더 짧은 쪽**을 선택하는 역설이
 * 생긴다(재결제를 시도할수록 보호가 줄어드는 비단조 결함). 같은 결함이 방향만 바꿔 3번
 * 재발한 근본 원인이 여기 있다 — **지켜야 할 불변식(증거가 추가되면 보호 창은 절대 짧아지지
 * 않는다 = 단조성)을 "승자 하나" 구조로는 표현할 수 없었다.**
 *
 * 그래서 [Live]가 `since`(느린 TTL 앵커) 외에 `attemptSince`(빠른 TTL 앵커, nullable)도
 * 함께 들고 간다 — live 신호가 하나라도 있으면 승자를 고르지 않고 **양쪽 앵커를 모두**
 * 소비측에 전달한다. 소비측은 두 창(느린 TTL by `since`, 빠른 TTL by `attemptSince`)이
 * **모두** 닫혔을 때만 만료를 허용해야 한다 — 이 AND 결합이 단조성을 보장한다: attempting
 * 증거가 새로 추가돼도 `since`(느린 TTL) 조건은 그대로 남아 있으므로 이미 보호되던 대상이
 * 새삼 풀리지 않고, 오히려 새 증거가 빠른 TTL 창까지 열어 두어 보호가 늘어날 수만 있다
 * (줄어들 수 없다). 변이는 여전히 4개(Settled/Live/Attempting/None)로 유지한다 — payment
 * 상태를 1:1 미러링하는 변이를 늘리는 것이 아니라, 기존 [Live] 변이에 필드를 추가했을 뿐이다.
 *
 * - [Settled]: COMPLETED — 돈을 받았다. TTL 앵커 필드가 아예 없다 — 시간값을 실수로
 *   끼워 넣어 만료 판정에 쓸 수 없는 구조다(절대 만료 금지).
 * - [Live]: READY 또는 COMPLETED 중 [since](payment 행 생성 시각 — `markReady`로 checkoutUrl이
 *   발급되는 시각이 아니라 [com.sportsapp.domain.payment.dto.PaymentLivenessRow.createdAt]이다.
 *   READY 행은 PENDING으로 먼저 생성된 뒤 상태만 전이되므로 둘은 다르다)이 존재하는 상태 —
 *   느린 TTL 앵커. [attemptSince](8차)는 **같은 주문에 동시에 존재하는** PENDING(attempting)
 *   행의 최신 시각 — 없으면 `null`. 소비측은 `since`(느린 TTL)와 `attemptSince`(빠른 TTL,
 *   있을 때만) **양쪽 창이 모두 닫혔을 때만** 만료를 허용해야 한다(단조성 — 위 8차 참고).
 *   COMPLETED는 [Settled]로도 계산되므로(같은 주문이 두 변이 후보에 모두 해당해도 classify는
 *   [Settled] 하나만 반환한다), 소비측이 sealed `when`을 쓰면 [Settled] 분기가 구조적으로
 *   먼저 잡혀 [Live]로는 도달하지 않는다.
 * - [Attempting]: **live 행이 하나도 없을 때만** 반환된다 — PENDING(PG 왕복 대기 중, 아직
 *   checkoutUrl 미발급) 신호만 존재하는 상태. 빠른 TTL이나 앵커는 이 시도 시작 시각
 *   ([since])과 주문 생성 시각의 **최댓값**이어야 한다(6차 재설계 — p1. `createPending`이
 *   PENDING 행을 먼저 자기 트랜잭션으로 커밋하고, 그 뒤 PG 왕복(δ) 동안 그 행은 PENDING으로
 *   머문다 — 주문 생성 시각만 보고 만료를 허용하면 δ 안에 스위퍼가 돌 때 "방금 시작된
 *   재결제 시도"를 오만료시킨다). 최댓값 계산은 소비 도메인이 한다 — payment는 주문 생성
 *   시각을 모른다.
 * - [None]: 결제 시도 이력이 없거나 전부 종결(FAILED/CANCELLED/REFUNDED)만 있음 — 빠른 TTL,
 *   앵커는 소비 도메인이 소유한 주문 생성 시각을 그대로 쓴다.
 *
 * 시간 인자를 받지 않는다 — TTL(분값) 자체는 이 타입이 갖지 않는다. "슬롯을 얼마나 붙잡아
 * 둘 것인가"는 소비 도메인의 정책이므로 [Live]/[Attempting]은 발급·시도 시각([since])만
 * 반환하고, 몇 분을 허용할지는 소비 도메인(예: booking의 `BookingExpiryProperties`)이
 * 결정한다 — "payment는 사실만 답한다" 원칙과 충돌하지 않는다.
 *
 * **머지 전 하드닝 (리뷰 — 판정을 타입으로 강제)**: 8차까지는 "두 창을 모두 검사하라"는
 * 단조성 불변식이 [Live] 소비측(예: `BookingDomainService.isExpirable`)의 KDoc 의무로만
 * 존재했다 — 타입은 그 위반(예: `since.isBefore(readyThreshold)`만 보고 `attemptSince`를
 * 빠뜨리는 한 줄)을 컴파일 타임에 막지 못했다. booking(W1-11c)과 같은 구조를 goods·ticketing·
 * recruitment(W1-11a/b/d)가 각자 독립적으로 재구현해야 하는 이상, 이 실수는 네 번 반복될
 * 위험이 있었다. 그래서 판정 로직(`when`의 각 분기)을 소비측에서 이 인터페이스의
 * [allowsExpiry] 멤버로 끌어올린다 — 소비 도메인은 항상 `liveness.allowsExpiry(...)` 하나만
 * 호출하면 되고, [Live]의 AND 결합을 직접 재작성할 방법이 없다(캡슐화 위임 — Tell, Don't
 * Ask). `attemptSince`의 기본값(`= null`)도 제거했다 — 생성 지점이 [Live]를 갖는 유일한
 * production 코드는 `PaymentLivenessClassifier.classifyOrder`뿐이라 기본값 없이도 안전하고,
 * 기본값은 "가장 덜 보호적인 값(신호 없음)"이라 실수로 빠뜨리면 조용히 보호가 사라지는
 * 방향이었다.
 */
sealed interface OrderPaymentLiveness {

    /**
     * 이 판정이 만료를 허용하는지 계산한다. 소비 도메인(booking·goods·ticketing·recruitment)은
     * **항상 이 메서드로 위임**해야 하며, 변이별(Live/Attempting/None) 판정 로직을 `when`으로
     * 직접 재구현하지 않는다 — 특히 [Live]의 두 창(느린 TTL `since` + 빠른 TTL `attemptSince`)
     * AND 결합은 이 타입 안에서만 계산돼야, 소비측 재구현마다 반복돼 온 "한 항 누락" 결함
     * (6차·7차·8차 참고)이 구조적으로 재발할 수 없다.
     *
     * @param orderCreatedAt 소비 도메인이 소유한 주문(예약·상품 주문 등) 생성 시각 — [Attempting]/[None]
     *   갈래에서 `payment 시도 시각`·`readyThreshold`와의 `maxOf` 앵커로 쓰인다. payment 행이
     *   주문보다 먼저 생성될 수 없다는 데이터 전제 없이도 단조성이 성립하도록, [Live]도 이
     *   값을 `since`와 함께 `maxOf`로 묶는다(리뷰 p4 — 전제 제거).
     * @param readyThreshold 느린 TTL 기준 시각(`now - readyTtlMinutes`) — [Live] 갈래가 쓴다.
     * @param fastThreshold 빠른 TTL 기준 시각(`now - ttlMinutes`) — [Live]의 attempting 갈래,
     *   [Attempting], [None]이 쓴다.
     */
    fun allowsExpiry(orderCreatedAt: ZonedDateTime, readyThreshold: ZonedDateTime, fastThreshold: ZonedDateTime): Boolean

    /** COMPLETED — 돈을 받았다. 항상 만료 금지(TTL 앵커 필드가 아예 없어 실수로도 만료 계산에 쓸 수 없다). */
    data object Settled : OrderPaymentLiveness {
        override fun allowsExpiry(orderCreatedAt: ZonedDateTime, readyThreshold: ZonedDateTime, fastThreshold: ZonedDateTime): Boolean = false
    }

    /**
     * @property since 느린 TTL 앵커 — live(READY/COMPLETED) 행 중 최신 생성 시각.
     * @property attemptSince 빠른 TTL 앵커(8차) — 같은 주문에 함께 존재하는 attempting(PENDING)
     *   행 중 최신 생성 시각. 없으면 `null`. **단조성 불변식**: [allowsExpiry]는 `since`·
     *   `attemptSince` 두 창이 모두 닫혔을 때만 만료를 허용한다 — `attemptSince`가 새로
     *   채워져도 `since` 조건은 그대로 유지되므로, 증거(재결제 시도)가 추가된다고 해서 이미
     *   보호되던 대상이 풀리는 일은 없다(위 클래스 KDoc "8차 재설계" 참고).
     */
    data class Live(val since: ZonedDateTime, val attemptSince: ZonedDateTime?) : OrderPaymentLiveness {
        override fun allowsExpiry(orderCreatedAt: ZonedDateTime, readyThreshold: ZonedDateTime, fastThreshold: ZonedDateTime): Boolean =
            maxOf(orderCreatedAt, since).isBefore(readyThreshold) &&
                (attemptSince == null || maxOf(orderCreatedAt, attemptSince).isBefore(fastThreshold))
    }

    /** live 행이 하나도 없을 때만 반환 — 시도 시작 시각과 주문 생성 시각의 최댓값이 빠른 TTL을 지나야 만료 대상. */
    data class Attempting(val since: ZonedDateTime) : OrderPaymentLiveness {
        override fun allowsExpiry(orderCreatedAt: ZonedDateTime, readyThreshold: ZonedDateTime, fastThreshold: ZonedDateTime): Boolean =
            maxOf(orderCreatedAt, since).isBefore(fastThreshold)
    }

    /** 결제 시도 이력이 없거나 전부 종결(FAILED/CANCELLED/REFUNDED) — 주문 생성 시각만으로 빠른 TTL 재검증. */
    data object None : OrderPaymentLiveness {
        override fun allowsExpiry(orderCreatedAt: ZonedDateTime, readyThreshold: ZonedDateTime, fastThreshold: ZonedDateTime): Boolean =
            orderCreatedAt.isBefore(fastThreshold)
    }
}
