package com.sportsapp.domain.payment.dto

import com.sportsapp.domain.common.payment.OrderPaymentLiveness

/**
 * 만료 스위퍼(W1-11a~d 공통 만료 금지 가드)가 소비하는 결제 생존 상태 집합.
 *
 * `PaymentStatus`/`Payment` 엔티티를 소비 컨텍스트로 노출하지 않는다 — orderId별 판정
 * ([OrderPaymentLiveness])만 반환한다.
 *
 * **6차 재설계 (p3-3)**: 이전에는 `liveSince: Map<Long, ZonedDateTime>` +
 * `settledOrderIds: Set<Long>`를 나란히 노출해, "settled를 먼저 걸러야 한다"는 불변식이
 * KDoc에만 있고 소비 도메인이 각자 재구현해야 했다. 이제 sealed 타입([OrderPaymentLiveness])
 * 하나로 판정을 통일해, 소비측이 `when` 전수 분기를 쓰면 settled 우선 판정을 컴파일러가
 * 강제한다. [of]는 조회되지 않은(=payment 행이 아예 없는) orderId에 대해
 * [OrderPaymentLiveness.None]을 기본값으로 반환한다 — 호출부가 `Map.get`으로 null을 직접
 * 다루지 않게 한다.
 */
data class PaymentLivenessQueryResult(
    val livenessByOrderId: Map<Long, OrderPaymentLiveness>,
) {
    fun of(orderId: Long): OrderPaymentLiveness = livenessByOrderId[orderId] ?: OrderPaymentLiveness.None

    companion object {
        fun empty(): PaymentLivenessQueryResult = PaymentLivenessQueryResult(emptyMap())
    }
}
