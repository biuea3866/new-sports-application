package com.sportsapp.domain.goods.entity

/**
 * 한정판 판매 회차 상태.
 *
 * 영속 status는 조회·집계 표기용이며, 실제 구매 게이트 판정은
 * [LimitedDrop.validatePurchasable]이 now·remaining 기준으로 실시간 결정한다.
 */
enum class LimitedDropStatus {
    SCHEDULED,
    OPEN,
    SOLD_OUT,
    CLOSED,
    ;

    fun canTransitTo(next: LimitedDropStatus): Boolean = when (this) {
        SCHEDULED -> next == OPEN
        OPEN -> next == SOLD_OUT || next == CLOSED
        SOLD_OUT -> next == CLOSED
        CLOSED -> false
    }
}
