package com.sportsapp.application.goods

import com.sportsapp.domain.common.security.OwnershipGuard
import com.sportsapp.domain.goods.Product
import com.sportsapp.domain.goods.ProductCategory
import com.sportsapp.domain.goods.ProductRepository
import com.sportsapp.domain.goods.ProductStatus
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal

class ActivateMyProductUseCaseTest : BehaviorSpec({

    val productRepository = mockk<ProductRepository>()
    val ownershipGuard = mockk<OwnershipGuard>()
    val useCase = ActivateMyProductUseCase(productRepository, ownershipGuard)

    val authUserId = 42L
    val productId = 1L

    Given("INACTIVE 상태인 본인 소유 Product가 있을 때") {
        val product = Product(
            name = "테니스 라켓",
            category = ProductCategory.EQUIPMENT,
            price = BigDecimal("50000"),
            description = "고급 테니스 라켓",
            imageUrl = "https://example.com/racket.jpg",
            status = ProductStatus.INACTIVE,
            ownerId = authUserId,
        )

        every { ownershipGuard.authUserId() } returns authUserId
        every { productRepository.findById(productId) } returns product
        every { ownershipGuard.requireOwned(product.ownerId, authUserId) } returns Unit
        every { productRepository.save(product) } returns product

        When("[U-01] activate를 호출하면") {
            useCase.execute(ActivateMyProductCommand(productId))

            Then("Product.status가 ACTIVE로 전이된다") {
                product.status shouldBe ProductStatus.ACTIVE
            }

            Then("OwnershipGuard.requireOwned가 1회 호출된다") {
                verify(exactly = 1) { ownershipGuard.requireOwned(product.ownerId, authUserId) }
            }
        }
    }
})
