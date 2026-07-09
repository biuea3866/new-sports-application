package com.sportsapp.presentation.goods.dto.response

import com.sportsapp.domain.goods.vo.SellerType
import com.sportsapp.domain.goods.dto.ProductWithStock
import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.entity.ProductStatus
import com.sportsapp.domain.goods.vo.ProductCategory
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

private const val OWNER_USER_ID = 777L

/**
 * `ProductWithStockResponse.ownerId` additive 노출(BE-11, FE-14 본인상품 CTA 숨김) 회귀 +
 * [F5] image_url NULL 허용(V6 마이그레이션) 대비 imageUrl nullable 매핑 검증을 함께 다룬다.
 */
class ProductWithStockResponseTest : FunSpec({

    test("ProductWithStockResponse.of는 Product.ownerId를 그대로 노출한다") {
        val product = Product.create(
            name = "축구화",
            category = ProductCategory.FOOTWEAR,
            price = BigDecimal("30000"),
            description = "설명",
            imageUrl = "https://example.com/shoes.jpg",
            ownerUserId = OWNER_USER_ID,
            sellerType = SellerType.B2C,
        )
        product.activate()
        val productWithStock = ProductWithStock(product = product, stockQuantity = 5)

        val response = ProductWithStockResponse.of(productWithStock)

        response.ownerId shouldBe OWNER_USER_ID
        response.status shouldBe ProductStatus.ACTIVE
    }

    // [F5] image_url 컬럼은 NULL 허용(V6)인데 Product 엔티티는 Kotlin non-null String이다. Hibernate가
    // 리플렉션으로 null을 세팅해 로드 시점엔 예외가 없다가, 그 null이 응답 DTO의 non-null 파라미터로
    // 전달되는 순간 Intrinsics.checkNotNullParameter가 NPE를 던져 실측 500이 났다. DTO를 nullable로
    // 고쳐 해소 — 리플렉션으로 그 상태를 재현해 검증한다.
    test("image_url이 NULL인 상품(레거시 데이터)도 예외 없이 imageUrl=null 응답으로 매핑된다") {
        val product = Product.create(
            name = "이미지 없는 상품",
            category = ProductCategory.FOOTWEAR,
            price = BigDecimal("10000"),
            description = "설명",
            imageUrl = "placeholder",
            ownerUserId = OWNER_USER_ID,
            sellerType = SellerType.B2C,
        )
        val imageUrlField = Product::class.java.getDeclaredField("imageUrl")
        imageUrlField.isAccessible = true
        imageUrlField.set(product, null)
        val productWithStock = ProductWithStock(product = product, stockQuantity = 5)

        val response = shouldNotThrowAny { ProductWithStockResponse.of(productWithStock) }
        response.imageUrl.shouldBeNull()
    }

    test("imageUrl이 정상 설정된 상품은 그대로 반환한다 (회귀)") {
        val product = Product.create(
            name = "정상 상품",
            category = ProductCategory.FOOTWEAR,
            price = BigDecimal("10000"),
            description = "설명",
            imageUrl = "https://image.example.com/a.png",
            ownerUserId = OWNER_USER_ID,
            sellerType = SellerType.B2C,
        )
        val productWithStock = ProductWithStock(product = product, stockQuantity = 5)

        ProductWithStockResponse.of(productWithStock).imageUrl shouldBe "https://image.example.com/a.png"
    }
})
