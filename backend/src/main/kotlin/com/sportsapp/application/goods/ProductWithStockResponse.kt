package com.sportsapp.application.goods

import com.sportsapp.domain.goods.Product
import com.sportsapp.domain.goods.ProductCategory
import com.sportsapp.domain.goods.ProductStatus
import com.sportsapp.domain.goods.ProductWithStock
import java.math.BigDecimal

data class ProductWithStockResponse(
    val id: Long,
    val name: String,
    val category: ProductCategory,
    val price: BigDecimal,
    val description: String,
    val imageUrl: String,
    val status: ProductStatus,
    val stockQuantity: Int,
    @com.fasterxml.jackson.annotation.JsonIgnore
    val product: Product,
) {
    companion object {
        fun of(productWithStock: ProductWithStock): ProductWithStockResponse =
            ProductWithStockResponse(
                id = productWithStock.product.id,
                name = productWithStock.product.name,
                category = productWithStock.product.category,
                price = productWithStock.product.price,
                description = productWithStock.product.description,
                imageUrl = productWithStock.product.imageUrl,
                status = productWithStock.product.status,
                stockQuantity = productWithStock.stockQuantity,
                product = productWithStock.product,
            )
    }
}
