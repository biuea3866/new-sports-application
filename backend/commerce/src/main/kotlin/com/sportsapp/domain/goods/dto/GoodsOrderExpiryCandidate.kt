package com.sportsapp.domain.goods.dto

import java.time.ZonedDateTime

/**
 * W1-11a 만료 스위퍼 후보 조회 결과 — 주문 id와 생성 시각(createdAt)을 함께 담는다.
 *
 * `facility-booking`(W1-11c)의 `BookingExpiryCandidate`와 동일한 이유로 createdAt이
 * 필요하다 — [com.sportsapp.domain.common.payment.OrderPaymentLiveness.allowsExpiry]가
 * `orderCreatedAt`을 [Attempting]/[None] 갈래의 앵커로, [Live]도 `maxOf(orderCreatedAt, since)`로
 * 함께 묶어 단조성을 보장하는 데 쓴다(공유 커널 KDoc 참고). 이 createdAt은 GoodsOrder 생성
 * 시각 그 자체이며, 결제 발급/시도 시각은 payment가 별도로 반환한다.
 */
data class GoodsOrderExpiryCandidate(
    val orderId: Long,
    val createdAt: ZonedDateTime,
)
