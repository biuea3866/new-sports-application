package com.sportsapp.application.goods.usecase

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.common.security.OwnershipGuard
import com.sportsapp.domain.goods.service.GoodsDomainService
import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.vo.ProductCategory
import com.sportsapp.domain.goods.entity.ProductStatus
import com.sportsapp.domain.goods.dto.ProductWithStock
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal

class DeactivateMyProductUseCaseTest : BehaviorSpec({

    val goodsDomainService = mockk<GoodsDomainService>()
    val ownershipGuard = mockk<OwnershipGuard>()
    val useCase = DeactivateMyProductUseCase(goodsDomainService, ownershipGuard)


    Given("ACTIVE 상태의 내 상품이 있을 때") {
        val ownerUserId = 42L
        val productId = 10L

        val product = Product(
            name = "테니스 라켓",
            category = ProductCategory.EQUIPMENT,
            price = BigDecimal("50000"),
            description = "프리미엄 라켓",
            imageUrl = "https://example.com/racket.jpg",
            status = ProductStatus.INACTIVE,
            ownerId = ownerUserId,
        )

        every { ownershipGuard.authUserId() } returns ownerUserId
        every {
            goodsDomainService.deactivateProductWithStock(productId, ownerUserId)
        } returns ProductWithStock(product = product, stockQuantity = 5)

        When("[U-01] execute 호출하면") {
            val result = useCase.execute(productId)

            Then("ownership 검증 후 status가 INACTIVE로 전이된 응답이 반환된다") {
                verify { ownershipGuard.authUserId() }
                verify { goodsDomainService.deactivateProductWithStock(productId, ownerUserId) }
                result.product.status shouldBe ProductStatus.INACTIVE
            }
        }
    }

    Given("다른 사용자의 상품 ID로 비활성화 요청 시") {
        val authUserId = 42L
        val otherOwnerId = 99L
        val productId = 10L

        every { ownershipGuard.authUserId() } returns authUserId
        every {
            goodsDomainService.deactivateProductWithStock(productId, authUserId)
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
