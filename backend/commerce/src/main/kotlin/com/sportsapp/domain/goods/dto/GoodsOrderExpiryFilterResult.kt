package com.sportsapp.domain.goods.dto

/**
 * [com.sportsapp.domain.goods.service.GoodsDomainService.filterExpirable] 판정 결과.
 *
 * `skippedSettledCount`를 `expirableIds` 제외분과 분리해 반환하는 이유는
 * `facility-booking`(W1-11c)의 `BookingExpiryFilterResult`와 동일하다 — settled(결제
 * 완료)로 건너뛴 건은 웹훅 유실·컨슈머 다운으로 "돈은 받았는데 주문이 여전히 PENDING"인
 * 환불 판단이 필요한 이상 신호이고, live(결제 진행 중)로 건너뛴 건은 정상 흐름이라 두
 * 사유를 뭉뚱그리면 경보가 불가능하다.
 */
data class GoodsOrderExpiryFilterResult(
    val expirableIds: List<Long>,
    val skippedSettledCount: Int,
)
