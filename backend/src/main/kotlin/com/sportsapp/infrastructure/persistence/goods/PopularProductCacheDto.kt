package com.sportsapp.infrastructure.persistence.goods

import com.sportsapp.domain.goods.Product
import com.sportsapp.domain.goods.ProductCategory
import com.sportsapp.domain.goods.ProductStatus
import java.math.BigDecimal

data class PopularProductCacheDto(
    val id: Long,
    val name: String,
    val category: ProductCategory,
    val price: BigDecimal,
    val description: String,
    val imageUrl: String,
    val status: ProductStatus,
) {
    fun toProduct(): Product = Product(
        name = name,
        category = category,
        price = price,
        description = description,
        imageUrl = imageUrl,
        status = status,
    )

    companion object {
        fun of(product: Product): PopularProductCacheDto = PopularProductCacheDto(
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
