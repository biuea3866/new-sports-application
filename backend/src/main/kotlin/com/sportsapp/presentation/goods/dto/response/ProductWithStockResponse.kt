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
    val imageUrl: String?,
    val status: ProductStatus,
    val stockQuantity: Int,
    /** 이 상품에 연결된 활성 한정판 회차 id. 없으면 null — FE는 이 값이 있을 때만 진입점 배너를 노출한다. */
    val limitedDropId: Long?,
    /** 상품 소유자(판매자) userId (BE-11, additive) — FE-14 본인상품 CTA 숨김 판정용. */
    val ownerId: Long,
    @com.fasterxml.jackson.annotation.JsonIgnore
    val product: Product,
) {
    companion object {
        fun of(productWithStock: ProductWithStock): ProductWithStockResponse =
            ProductWithStockResponse(
                id = productWithStock.product.id,
                name = productWithStock.product.name,
                category = productWithStock.product.category,
                price = productWithStock.price,
                description = productWithStock.product.description,
                imageUrl = productWithStock.product.imageUrl,
                status = productWithStock.product.status,
                stockQuantity = productWithStock.stockQuantity,
                limitedDropId = productWithStock.limitedDropId,
                ownerId = productWithStock.product.ownerId,
                product = productWithStock.product,
            )
    }
}
