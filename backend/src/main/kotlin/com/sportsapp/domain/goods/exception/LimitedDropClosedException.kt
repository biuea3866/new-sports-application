package com.sportsapp.domain.goods.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

/**
 * 판매 종료(now >= closeAt) 이후 구매를 시도하면 던진다.
 */
class LimitedDropClosedException(dropId: Long) : BusinessException(
    errorCode = "LIMITED_DROP_CLOSED",
    message = "판매가 종료된 회차입니다: dropId=$dropId"
) {
    override val status: ErrorStatus = ErrorStatus.CONFLICT
}
