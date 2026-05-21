package com.sportsapp.application.goods

import com.sportsapp.domain.goods.ProductCategory
import java.math.BigDecimal

data class CreateMyProductCommand(
    val name: String,
    val category: ProductCategory,
    val price: BigDecimal,
    val description: String,
    val imageUrl: String,
)
