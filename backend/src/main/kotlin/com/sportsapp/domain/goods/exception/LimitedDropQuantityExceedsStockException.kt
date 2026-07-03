package com.sportsapp.domain.goods.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

/**
 * 회차 개설 요청 수량(limitedQuantity)이 상품의 현재 재고를 초과할 때 던진다.
 */
class LimitedDropQuantityExceedsStockException(
    productId: Long,
    limitedQuantity: Int,
    stockQuantity: Int,
) : BusinessException(
    errorCode = "LIMITED_DROP_QUANTITY_EXCEEDS_STOCK",
    message = "한정 수량이 재고를 초과합니다: productId=$productId, limitedQuantity=$limitedQuantity, stock=$stockQuantity"
) {
    override val status: ErrorStatus = ErrorStatus.CONFLICT
}
