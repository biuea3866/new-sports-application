package com.sportsapp.presentation.b2b

import com.sportsapp.application.goods.UpdateMyProductCommand
import com.sportsapp.domain.goods.ProductCategory
import java.math.BigDecimal

data class UpdateMyProductRequest(
    val name: String?,
    val category: ProductCategory?,
    val price: BigDecimal?,
    val description: String?,
    val imageUrl: String?,
) {
    fun toCommand(productId: Long): UpdateMyProductCommand = UpdateMyProductCommand(
        productId = productId,
        name = name,
        category = category,
        price = price,
        description = description,
        imageUrl = imageUrl,
    )
}
