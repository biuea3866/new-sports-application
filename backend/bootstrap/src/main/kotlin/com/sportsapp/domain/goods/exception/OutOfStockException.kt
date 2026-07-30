package com.sportsapp.domain.goods.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class OutOfStockException(productId: Long, requested: Int, available: Int) : BusinessException(
    errorCode = "OUT_OF_STOCK",
    message = "상품 $productId 재고 부족: 요청 $requested, 가용 $available"
) {
    override val status: ErrorStatus = ErrorStatus.CONFLICT
}
