package com.sportsapp.domain.payment.dto

/**
 * 만료 스위퍼(W1-11a~d 공통 만료 금지 가드)가 소비하는 결제 생존 상태 집합.
 *
 * `PaymentStatus`/`Payment` 엔티티를 소비 컨텍스트로 노출하지 않는다 — orderId 집합만
 * 반환한다. 한 주문에 payment 행이 여러 건이어도(재시도로 idempotencyKey를 바꿔 재개시한
 * 경우 등) 그중 하나라도 해당 판정이면 그 orderId는 결과 Set에 포함된다.
 *
 * - [liveOrderIds]: 상태가 READY 또는 COMPLETED인 주문 — 결제가 시작이라도 됐다는 신호.
 * - [settledOrderIds]: 상태가 COMPLETED인 주문 — 돈을 받은 주문. 항상 [liveOrderIds]의 부분집합.
 */
data class PaymentLivenessQueryResult(
    val liveOrderIds: Set<Long>,
    val settledOrderIds: Set<Long>,
) {
    companion object {
        fun empty(): PaymentLivenessQueryResult = PaymentLivenessQueryResult(emptySet(), emptySet())
    }
}
