package com.sportsapp.domain.goods.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus
import com.sportsapp.domain.goods.entity.LimitedDropStatus

/**
 * [LimitedDropStatus.canTransitTo]가 거부하는 상태 전이를 시도하면 던진다
 * (예: CLOSED → OPEN). GoodsOrder/Booking의 InvalidXxxStateException과 동일 패턴.
 */
class InvalidLimitedDropStateException(
    from: LimitedDropStatus,
    to: LimitedDropStatus,
) : BusinessException(
    errorCode = "INVALID_LIMITED_DROP_STATE",
    message = "한정판 회차 상태 전이 불가: $from → $to"
) {
    override val status: ErrorStatus = ErrorStatus.CONFLICT
}
