package com.sportsapp.domain.goods.dto

import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.vo.ProductCategory
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

private const val OWNER_USER_ID = 20L

/**
 * [F5] PopularProductSnapshot.of(product)도 ProductWithStockResponse와 동일하게 product.imageUrl을
 * non-null 생성자 파라미터로 그대로 전달한다 — image_url이 NULL인 상품이 인기 상품 목록에 포함되면
 * 동일한 NPE(Intrinsics.checkNotNullParameter)로 /products/popular 가 500이 된다.
 */
class PopularProductSnapshotTest : BehaviorSpec({

    fun productWithNullImageUrl(): Product {
        val product = Product.create(
            name = "이미지 없는 인기 상품",
            category = ProductCategory.FOOTWEAR,
            price = BigDecimal("20000"),
            description = "설명",
            imageUrl = "placeholder",
            ownerUserId = OWNER_USER_ID,
        )
        val field = Product::class.java.getDeclaredField("imageUrl")
        field.isAccessible = true
        field.set(product, null)
        return product
    }

    Given("image_url 컬럼이 NULL인 상품이 리플렉션으로 로드된 상황(레거시 데이터)") {
        val product = productWithNullImageUrl()

        When("PopularProductSnapshot으로 매핑하면") {
            Then("예외 없이 imageUrl이 null인 스냅샷을 만든다") {
                val snapshot = shouldNotThrowAny { PopularProductSnapshot.of(product) }
                snapshot.imageUrl.shouldBeNull()
            }
        }
    }

    Given("imageUrl이 정상적으로 설정된 상품이 있는 상황") {
        val product = Product.create(
            name = "정상 인기 상품",
            category = ProductCategory.FOOTWEAR,
            price = BigDecimal("20000"),
            description = "설명",
            imageUrl = "https://image.example.com/popular.png",
            ownerUserId = OWNER_USER_ID,
        )

        When("PopularProductSnapshot으로 매핑하면") {
            Then("imageUrl을 그대로 반환한다 (회귀)") {
                PopularProductSnapshot.of(product).imageUrl shouldBe "https://image.example.com/popular.png"
            }
        }
    }
})
