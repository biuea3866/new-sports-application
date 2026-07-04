package com.sportsapp.domain.goods.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

/**
 * 회차가 SOLD_OUT 상태일 때 구매를 시도하면 던진다 (FR-8, DB 미도달 즉시 거부).
 */
class LimitedDropSoldOutException(dropId: Long) : BusinessException(
    errorCode = "LIMITED_DROP_SOLD_OUT",
    message = "품절된 회차입니다: dropId=$dropId"
) {
    override val status: ErrorStatus = ErrorStatus.CONFLICT
}
