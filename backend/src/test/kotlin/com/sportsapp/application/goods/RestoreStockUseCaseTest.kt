package com.sportsapp.application.goods

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.common.security.OwnershipGuard
import com.sportsapp.domain.goods.GoodsDomainService
import com.sportsapp.domain.goods.Product
import com.sportsapp.domain.goods.ProductCategory
import com.sportsapp.domain.goods.ProductRepository
import com.sportsapp.domain.goods.ProductStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal

class RestoreStockUseCaseTest : BehaviorSpec({

    val goodsDomainService = mockk<GoodsDomainService>()
    val productRepository = mockk<ProductRepository>()
    val ownershipGuard = mockk<OwnershipGuard>()
    val useCase = RestoreStockUseCase(goodsDomainService, productRepository, ownershipGuard)

    val authUserId = 42L
    val productId = 1L

    val product = Product(
        name = "테니스 라켓",
        category = ProductCategory.EQUIPMENT,
        price = BigDecimal("50000"),
        description = "고급 테니스 라켓",
        imageUrl = "https://example.com/racket.jpg",
        status = ProductStatus.ACTIVE,
        ownerId = authUserId,
    )

    Given("본인 소유 Product에 재고 보충 요청을 하면") {
        val command = RestoreStockCommand(productId = productId, quantity = 10)

        every { ownershipGuard.authUserId() } returns authUserId
        every { productRepository.findById(productId) } returns product
        every { ownershipGuard.requireOwned(product.ownerId, authUserId) } returns Unit
        every { goodsDomainService.restoreStock(productId, 10) } returns Unit

        When("[U-02] execute를 호출하면") {
            useCase.execute(command)

            Then("OwnershipGuard.requireOwned가 1회 호출된다") {
                verify(exactly = 1) { ownershipGuard.requireOwned(product.ownerId, authUserId) }
            }

            Then("GoodsDomainService.restoreStock가 1회 호출된다") {
                verify(exactly = 1) { goodsDomainService.restoreStock(productId, 10) }
            }
        }
    }

    Given("존재하지 않는 productId로 요청하면") {
        val command = RestoreStockCommand(productId = 999L, quantity = 10)

        every { ownershipGuard.authUserId() } returns authUserId
        every { productRepository.findById(999L) } returns null

        When("execute를 호출하면") {
            Then("ResourceNotFoundException이 발생한다") {
                shouldThrow<ResourceNotFoundException> {
                    useCase.execute(command)
                }
            }
        }
    }
})
