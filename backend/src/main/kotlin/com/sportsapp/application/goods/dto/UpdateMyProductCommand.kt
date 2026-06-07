package com.sportsapp.application.goods.dto

import com.sportsapp.domain.goods.vo.ProductCategory
import java.math.BigDecimal

data class UpdateMyProductCommand(
    val productId: Long,
    val name: String?,
    val category: ProductCategory?,
    val price: BigDecimal?,
    val description: String?,
    val imageUrl: String?,
)
