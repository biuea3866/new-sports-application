package com.sportsapp.application.goods

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.common.security.OwnershipGuard
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

class GetMyProductUseCaseTest : BehaviorSpec({

    val goodsDomainService = mockk<GoodsDomainService>()
    val ownershipGuard = mockk<OwnershipGuard>()
    val useCase = GetMyProductUseCase(goodsDomainService, ownershipGuard)


    Given("내 상품이 존재할 때") {
        val ownerUserId = 42L
        val productId = 10L

        val product = Product(
            name = "테니스 라켓",
            category = ProductCategory.EQUIPMENT,
            price = BigDecimal("50000"),
            description = "프리미엄 라켓",
            imageUrl = "https://example.com/racket.jpg",
            status = ProductStatus.ACTIVE,
            ownerId = ownerUserId,
        )

        every { ownershipGuard.authUserId() } returns ownerUserId
        every {
            goodsDomainService.getProductByIdAndOwnerId(productId, ownerUserId)
        } returns ProductWithStock(product = product, stockQuantity = 7)

        When("[U-01] execute 호출하면") {
            val result = useCase.execute(productId)

            Then("상품 정보와 재고 수량이 반환된다") {
                verify { ownershipGuard.authUserId() }
                verify { goodsDomainService.getProductByIdAndOwnerId(productId, ownerUserId) }
                result.name shouldBe "테니스 라켓"
                result.stockQuantity shouldBe 7
            }
        }
    }

    Given("다른 사용자의 상품 ID로 조회 시") {
        val authUserId = 42L
        val productId = 10L

        every { ownershipGuard.authUserId() } returns authUserId
        every {
            goodsDomainService.getProductByIdAndOwnerId(productId, authUserId)
        } throws ResourceNotFoundException("Product", productId)

        When("[U-02] execute 호출하면") {
            Then("ResourceNotFoundException이 발생한다") {
                shouldThrow<ResourceNotFoundException> {
                    useCase.execute(productId)
                }
                verify { ownershipGuard.authUserId() }
            }
        }
    }
})
