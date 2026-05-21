package com.sportsapp.application.goods

import com.sportsapp.domain.goods.PopularProductSnapshot
import com.sportsapp.domain.goods.ProductCategory
import java.math.BigDecimal

data class PopularProductResponse(
    val id: Long,
    val name: String,
    val category: ProductCategory,
    val price: BigDecimal,
) {
    companion object {
        fun of(snapshot: PopularProductSnapshot): PopularProductResponse =
            PopularProductResponse(
                id = snapshot.id,
                name = snapshot.name,
                category = snapshot.category,
                price = snapshot.price,
            )
    }
}
