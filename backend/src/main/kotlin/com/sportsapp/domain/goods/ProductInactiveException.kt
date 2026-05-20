package com.sportsapp.domain.goods

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class ProductInactiveException(productId: Long) : BusinessException(
    errorCode = "PRODUCT_INACTIVE",
    message = "비활성화된 상품입니다: $productId"
) {
    override val status: ErrorStatus = ErrorStatus.UNPROCESSABLE
}
