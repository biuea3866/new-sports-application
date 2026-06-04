package com.sportsapp.domain.goods.dto

import java.math.BigDecimal
import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.entity.ProductStatus
import com.sportsapp.domain.goods.vo.ProductCategory

data class PopularProductSnapshot(
    val id: Long,
    val name: String,
    val category: ProductCategory,
    val price: BigDecimal,
    val description: String,
    val imageUrl: String,
    val status: ProductStatus,
) {
    companion object {
        fun of(product: Product): PopularProductSnapshot = PopularProductSnapshot(
            id = product.id,
            name = product.name,
            category = product.category,
            price = product.price,
            description = product.description,
            imageUrl = product.imageUrl,
            status = product.status,
        )
    }
}
