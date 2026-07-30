package com.sportsapp.domain.goods.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

/**
 * 사용자의 1인 구매 한도(FR-6)를 초과해
 * [com.sportsapp.domain.goods.gateway.ReservationResult.PerUserLimitExceeded]가 반환되면 던진다.
 * [limit]은 초과 기준이 된 한도값이다.
 */
class LimitedDropPerUserLimitExceededException(
    dropId: Long,
    val limit: Int,
) : BusinessException(
    errorCode = "LIMITED_DROP_PER_USER_LIMIT_EXCEEDED",
    message = "1인 구매 한도를 초과했습니다: dropId=$dropId, limit=$limit"
) {
    override val status: ErrorStatus = ErrorStatus.FORBIDDEN
}
