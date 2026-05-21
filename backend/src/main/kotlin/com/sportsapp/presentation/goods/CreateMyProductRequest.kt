package com.sportsapp.presentation.goods

import com.sportsapp.application.goods.CreateMyProductCommand
import com.sportsapp.domain.goods.ProductCategory
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

data class CreateMyProductRequest(
    @field:NotBlank
    val name: String,

    @field:NotNull
    val category: ProductCategory,

    @field:NotNull
    @field:DecimalMin("0.01")
    val price: BigDecimal,

    @field:NotBlank
    val description: String,

    @field:NotBlank
    val imageUrl: String,
) {
    fun toCommand() = CreateMyProductCommand(
        name = name,
        category = category,
        price = price,
        description = description,
        imageUrl = imageUrl,
    )
}
