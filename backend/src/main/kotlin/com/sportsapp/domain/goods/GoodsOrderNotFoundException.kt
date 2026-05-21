package com.sportsapp.domain.goods

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class GoodsOrderNotFoundException(orderId: Long) : BusinessException(
    errorCode = "GOODS_ORDER_NOT_FOUND",
    message = "주문을 찾을 수 없습니다: $orderId"
) {
    override val status: ErrorStatus = ErrorStatus.NOT_FOUND
}
