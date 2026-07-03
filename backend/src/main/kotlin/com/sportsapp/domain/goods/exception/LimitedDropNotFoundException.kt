package com.sportsapp.domain.goods.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

/**
 * dropId로 LimitedDrop을 찾을 수 없을 때 던진다.
 */
class LimitedDropNotFoundException(dropId: Long) : BusinessException(
    errorCode = "LIMITED_DROP_NOT_FOUND",
    message = "한정판 회차를 찾을 수 없습니다: $dropId"
) {
    override val status: ErrorStatus = ErrorStatus.NOT_FOUND
}
