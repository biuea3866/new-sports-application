package com.sportsapp.application.goods.usecase

import com.sportsapp.domain.common.security.OwnershipGuard
import com.sportsapp.domain.goods.service.GoodsDomainService
import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.vo.ProductCategory
import com.sportsapp.domain.goods.entity.ProductStatus
import com.sportsapp.domain.goods.dto.ProductWithStock
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal

class ActivateMyProductUseCaseTest : BehaviorSpec({

    val goodsDomainService = mockk<GoodsDomainService>()
    val ownershipGuard = mockk<OwnershipGuard>()
    val useCase = ActivateMyProductUseCase(goodsDomainService, ownershipGuard)

    Given("INACTIVE 상태의 내 상품이 있을 때") {
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
            goodsDomainService.activateProductWithStock(productId, ownerUserId)
        } returns ProductWithStock(product = product, stockQuantity = 5)

        When("activate 호출하면") {
            val result = useCase.execute(productId)

            Then("ownership 검증 후 status가 ACTIVE로 전이된 응답이 반환된다") {
                verify(exactly = 1) { ownershipGuard.authUserId() }
                result.product.status shouldBe ProductStatus.ACTIVE
            }
        }
    }
})
