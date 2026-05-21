package com.sportsapp.presentation.b2b

import com.sportsapp.application.goods.CreateMyProductCommand
import com.sportsapp.domain.goods.ProductCategory
import java.math.BigDecimal

data class CreateMyProductRequest(
    val name: String,
    val category: ProductCategory,
    val price: BigDecimal,
    val description: String,
    val imageUrl: String,
) {
    fun toCommand(): CreateMyProductCommand = CreateMyProductCommand(
        name = name,
        category = category,
        price = price,
        description = description,
        imageUrl = imageUrl,
    )
}
