package com.sportsapp.presentation.goods.dto.response

import com.sportsapp.domain.goods.dto.ProductWithStock
import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.entity.ProductStatus
import com.sportsapp.domain.goods.vo.ProductCategory
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

/**
 * `ProductWithStockResponse.ownerId` additive 노출 (BE-11) — FE-14 본인상품 CTA 숨김 활성화용.
 * 기존 필드·`of` 팩토리 시그니처는 하위 호환을 유지한다.
 */
class ProductWithStockResponseTest : FunSpec({

    test("ProductWithStockResponse.of는 Product.ownerId를 그대로 노출한다") {
        val product = Product.create(
            name = "축구화",
            category = ProductCategory.FOOTWEAR,
            price = BigDecimal("30000"),
            description = "설명",
            imageUrl = "https://example.com/shoes.jpg",
            ownerUserId = 777L,
        )
        product.activate()
        val productWithStock = ProductWithStock(product = product, stockQuantity = 5)

        val response = ProductWithStockResponse.of(productWithStock)

        response.ownerId shouldBe 777L
        response.status shouldBe ProductStatus.ACTIVE
    }
})
