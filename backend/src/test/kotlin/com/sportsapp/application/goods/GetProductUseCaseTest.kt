package com.sportsapp.application.goods

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.goods.GoodsDomainService
import com.sportsapp.domain.goods.Product
import com.sportsapp.domain.goods.ProductCategory
import com.sportsapp.domain.goods.ProductStatus
import com.sportsapp.domain.goods.ProductWithStock
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal

class GetProductUseCaseTest : BehaviorSpec({

    val goodsDomainService = mockk<GoodsDomainService>()
    val useCase = GetProductUseCase(goodsDomainService)

    Given("존재하는 활성 상품 id로 조회 시") {
        val productId = 1L
        val product = Product(
            name = "테니스 라켓",
            category = ProductCategory.EQUIPMENT,
            price = BigDecimal("50000"),
            description = "프리미엄 라켓",
            imageUrl = "https://example.com/racket.jpg",
            status = ProductStatus.ACTIVE,
            ownerId = 10L,
        )
        every { goodsDomainService.getProductWithStock(productId) } returns ProductWithStock(
            product = product,
            stockQuantity = 5,
        )

        When("[U-01] execute 호출하면") {
            val result = useCase.execute(productId)

            Then("상품 상세(가격/재고/판매상태)가 반환된다") {
                verify { goodsDomainService.getProductWithStock(productId) }
                result.name shouldBe "테니스 라켓"
                result.price shouldBe BigDecimal("50000")
                result.stockQuantity shouldBe 5
                result.status shouldBe ProductStatus.ACTIVE
            }
        }
    }

    Given("존재하지 않거나 soft-delete된 상품 id로 조회 시") {
        val productId = 999L
        every { goodsDomainService.getProductWithStock(productId) } throws ResourceNotFoundException("Product", productId)

        When("[U-02] execute 호출하면") {
            Then("ResourceNotFoundException이 발생한다") {
                shouldThrow<ResourceNotFoundException> {
                    useCase.execute(productId)
                }
                verify { goodsDomainService.getProductWithStock(productId) }
            }
        }
    }
})
