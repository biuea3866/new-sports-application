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
import com.sportsapp.application.goods.dto.UpdateMyProductCommand

class UpdateMyProductUseCaseTest : BehaviorSpec({

    val goodsDomainService = mockk<GoodsDomainService>()
    val ownershipGuard = mockk<OwnershipGuard>()
    val useCase = UpdateMyProductUseCase(goodsDomainService, ownershipGuard)


    Given("내 상품 수정 요청을 보낼 때") {
        val ownerUserId = 42L
        val command = UpdateMyProductCommand(
            productId = 10L,
            name = "수정된 라켓",
            category = ProductCategory.EQUIPMENT,
            price = BigDecimal("60000"),
            description = "수정된 설명",
            imageUrl = "https://example.com/updated.jpg",
        )

        val updatedProduct = Product(
            name = "수정된 라켓",
            category = ProductCategory.EQUIPMENT,
            price = BigDecimal("60000"),
            description = "수정된 설명",
            imageUrl = "https://example.com/updated.jpg",
            status = ProductStatus.INACTIVE,
            ownerId = ownerUserId,
        )

        every { ownershipGuard.authUserId() } returns ownerUserId
        every {
            goodsDomainService.updateProduct(
                productId = command.productId,
                ownerUserId = ownerUserId,
                name = command.name,
                category = command.category,
                price = command.price,
                description = command.description,
                imageUrl = command.imageUrl,
            )
        } returns ProductWithStock(product = updatedProduct, stockQuantity = 3)

        When("[U-01] execute 호출하면") {
            val result = useCase.execute(command)

            Then("authUserId가 ownerUserId로 주입되어 DomainService를 호출하고 수정된 응답이 반환된다") {
                verify { ownershipGuard.authUserId() }
                verify {
                    goodsDomainService.updateProduct(
                        productId = command.productId,
                        ownerUserId = ownerUserId,
                        name = command.name,
                        category = command.category,
                        price = command.price,
                        description = command.description,
                        imageUrl = command.imageUrl,
                    )
                }
                result.product.name shouldBe "수정된 라켓"
                result.product.price shouldBe BigDecimal("60000")
            }
        }
    }

    Given("다른 사용자의 상품 ID로 수정 요청 시") {
        val authUserId = 42L
        val command = UpdateMyProductCommand(
            productId = 10L,
            name = "해킹 시도",
            category = null,
            price = null,
            description = null,
            imageUrl = null,
        )

        every { ownershipGuard.authUserId() } returns authUserId
        every {
            goodsDomainService.updateProduct(
                productId = command.productId,
                ownerUserId = authUserId,
                name = command.name,
                category = command.category,
                price = command.price,
                description = command.description,
                imageUrl = command.imageUrl,
            )
        } throws ResourceNotFoundException("Product", command.productId)

        When("[U-02] execute 호출하면") {
            Then("ResourceNotFoundException이 발생한다") {
                shouldThrow<ResourceNotFoundException> {
                    useCase.execute(command)
                }
                verify { ownershipGuard.authUserId() }
            }
        }
    }
})
