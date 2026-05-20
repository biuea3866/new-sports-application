package com.sportsapp.domain.goods

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class CartAccessDeniedException(itemId: Long) : BusinessException(
    errorCode = "CART_ACCESS_DENIED",
    message = "해당 장바구니 항목에 접근 권한이 없습니다: $itemId"
) {
    override val status: ErrorStatus = ErrorStatus.FORBIDDEN
}
