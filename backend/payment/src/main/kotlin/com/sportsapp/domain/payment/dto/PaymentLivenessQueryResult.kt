package com.sportsapp.domain.payment.dto

import java.time.ZonedDateTime

/**
 * 만료 스위퍼(W1-11a~d 공통 만료 금지 가드)가 소비하는 결제 생존 상태 집합.
 *
 * `PaymentStatus`/`Payment` 엔티티를 소비 컨텍스트로 노출하지 않는다 — orderId 기준 값만
 * 반환한다.
 *
 * - [liveSince]: orderId → live(READY 또는 COMPLETED) payment의 createdAt **최댓값**.
 *   한 주문에 payment 행이 여러 건인 것은 정상 흐름이다(장바구니 K1 + 결제 페이지 K2 재개시
 *   등). 최솟값·임의 1건을 쓰면 "오래된 READY + 방금 READY" 조합에서 판정이 뒤집혀
 *   오만료가 나므로 반드시 최댓값을 앵커로 쓴다(5차 재설계 — 앵커를 booking.createdAt에서
 *   payment 발급 시각으로 이전). live payment가 없는 orderId는 이 맵에 없다.
 * - [settledOrderIds]: 상태가 COMPLETED인 주문 — 돈을 받은 주문. orderId는 항상
 *   [liveSince]에도 존재한다(COMPLETED는 live이기도 하다).
 */
data class PaymentLivenessQueryResult(
    val liveSince: Map<Long, ZonedDateTime>,
    val settledOrderIds: Set<Long>,
) {
    companion object {
        fun empty(): PaymentLivenessQueryResult = PaymentLivenessQueryResult(emptyMap(), emptySet())
    }
}
