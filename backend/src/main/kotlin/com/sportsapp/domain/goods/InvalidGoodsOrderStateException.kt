package com.sportsapp.domain.goods

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class InvalidGoodsOrderStateException(
    current: GoodsOrderStatus,
    target: GoodsOrderStatus,
) : BusinessException(
    errorCode = "INVALID_GOODS_ORDER_STATE",
    message = "주문 상태 전이 불가: $current → $target"
) {
    override val status: ErrorStatus = ErrorStatus.CONFLICT
}
