package com.sportsapp.domain.goods

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class InvalidQuantityException(quantity: Int) : BusinessException(
    errorCode = "INVALID_QUANTITY",
    message = "유효하지 않은 수량입니다: $quantity"
) {
    override val status: ErrorStatus = ErrorStatus.UNPROCESSABLE
}
