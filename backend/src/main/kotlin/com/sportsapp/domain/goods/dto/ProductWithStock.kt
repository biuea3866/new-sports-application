package com.sportsapp.domain.goods.dto
import com.sportsapp.domain.goods.entity.Product

data class ProductWithStock(
    val product: Product,
    val stockQuantity: Int,
)
