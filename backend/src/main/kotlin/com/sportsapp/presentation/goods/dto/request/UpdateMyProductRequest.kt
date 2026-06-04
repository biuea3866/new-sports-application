package com.sportsapp.presentation.goods.dto.request

import com.sportsapp.application.goods.dto.UpdateMyProductCommand
import com.sportsapp.domain.goods.vo.ProductCategory
import jakarta.validation.constraints.DecimalMin
import java.math.BigDecimal

data class UpdateMyProductRequest(
    val name: String?,
    val category: ProductCategory?,
    @field:DecimalMin("0.01")
    val price: BigDecimal?,
    val description: String?,
    val imageUrl: String?,
) {
    fun toCommand(productId: Long) = UpdateMyProductCommand(
        productId = productId,
        name = name,
        category = category,
        price = price,
        description = description,
        imageUrl = imageUrl,
    )
}
