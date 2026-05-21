package com.sportsapp.application.goods

import com.sportsapp.domain.common.security.OwnershipGuard
import com.sportsapp.domain.goods.GoodsDomainService
import com.sportsapp.domain.goods.Product
import com.sportsapp.domain.goods.ProductCategory
import com.sportsapp.domain.goods.ProductStatus
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal

class CreateMyProductUseCaseTest : BehaviorSpec({

    val goodsDomainService = mockk<GoodsDomainService>()
    val ownershipGuard = mockk<OwnershipGuard>()
    val useCase = CreateMyProductUseCase(goodsDomainService, ownershipGuard)

    Given("GOODS_SELLER 사용자가 상품 등록 요청을 하면") {
        val authUserId = 42L
        val command = CreateMyProductCommand(
            name = "테니스 라켓",
            category = ProductCategory.EQUIPMENT,
            price = BigDecimal("50000"),
            description = "고급 테니스 라켓",
            imageUrl = "https://example.com/racket.jpg",
        )
        val createdProduct = Product(
            name = command.name,
            category = command.category,
            price = command.price,
            description = command.description,
            imageUrl = command.imageUrl,
            status = ProductStatus.INACTIVE,
            ownerId = authUserId,
        )

        every { ownershipGuard.authUserId() } returns authUserId
        every {
            goodsDomainService.createProduct(
                name = command.name,
                category = command.category,
                price = command.price,
                description = command.description,
                imageUrl = command.imageUrl,
                ownerUserId = authUserId,
            )
        } returns createdProduct

        When("[U-03] execute를 호출하면") {
            val result = useCase.execute(command)

            Then("ownerId가 authUserId로 자동 주입된 Product가 반환된다") {
                result.ownerId shouldBe authUserId
                result.name shouldBe command.name
                result.status shouldBe ProductStatus.INACTIVE
            }

            Then("goodsDomainService.createProduct가 authUserId와 함께 1회 호출된다") {
                verify(exactly = 1) {
                    goodsDomainService.createProduct(
                        name = command.name,
                        category = command.category,
                        price = command.price,
                        description = command.description,
                        imageUrl = command.imageUrl,
                        ownerUserId = authUserId,
                    )
                }
            }
        }
    }
})
