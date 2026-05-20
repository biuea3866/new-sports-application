package com.sportsapp.application.goods

import com.sportsapp.domain.goods.Product
import com.sportsapp.domain.goods.ProductCategory
import java.math.BigDecimal

data class PopularProductResponse(
    val id: Long,
    val name: String,
    val category: ProductCategory,
    val price: BigDecimal,
) {
    companion object {
        fun of(product: Product): PopularProductResponse =
            PopularProductResponse(
                id = product.id,
                name = product.name,
                category = product.category,
                price = product.price,
            )
    }
}
