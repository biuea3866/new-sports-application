package com.sportsapp.domain.goods.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class EmptyOrderException : BusinessException(
    errorCode = "EMPTY_ORDER",
    message = "주문 아이템이 비어 있습니다."
) {
    override val status: ErrorStatus = ErrorStatus.BAD_REQUEST
}
