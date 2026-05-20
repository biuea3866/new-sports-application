package com.sportsapp.domain.goods

data class ProductWithStock(
    val product: Product,
    val stockQuantity: Int,
)
