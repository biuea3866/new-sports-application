package com.sportsapp.domain.goods

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class ProductOwnershipException(productId: Long) : BusinessException(
    errorCode = "PRODUCT_NOT_FOUND",
    message = "Product not found: $productId"
) {
    override val status: ErrorStatus = ErrorStatus.NOT_FOUND
}
