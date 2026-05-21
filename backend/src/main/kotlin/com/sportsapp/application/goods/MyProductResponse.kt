package com.sportsapp.application.goods

import com.sportsapp.domain.goods.Product
import com.sportsapp.domain.goods.ProductCategory
import com.sportsapp.domain.goods.ProductStatus
import java.math.BigDecimal

data class MyProductResponse(
    val id: Long,
    val name: String,
    val category: ProductCategory,
    val price: BigDecimal,
    val description: String,
    val imageUrl: String,
    val status: ProductStatus,
    val ownerId: Long,
) {
    companion object {
        fun of(product: Product): MyProductResponse = MyProductResponse(
            id = product.id,
            name = product.name,
            category = product.category,
            price = product.price,
            description = product.description,
            imageUrl = product.imageUrl,
            status = product.status,
            ownerId = product.ownerId,
        )
    }
}
