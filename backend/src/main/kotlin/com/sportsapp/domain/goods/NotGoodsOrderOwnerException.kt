package com.sportsapp.domain.goods

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class NotGoodsOrderOwnerException(orderId: Long) : BusinessException(
    errorCode = "NOT_GOODS_ORDER_OWNER",
    message = "주문 접근 권한이 없습니다: $orderId"
) {
    override val status: ErrorStatus = ErrorStatus.FORBIDDEN
}
