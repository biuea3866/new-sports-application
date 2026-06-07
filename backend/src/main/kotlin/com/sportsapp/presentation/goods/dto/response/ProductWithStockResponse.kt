package com.sportsapp.presentation.goods.dto.response

import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.vo.ProductCategory
import com.sportsapp.domain.goods.entity.ProductStatus
import com.sportsapp.domain.goods.dto.ProductWithStock
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
