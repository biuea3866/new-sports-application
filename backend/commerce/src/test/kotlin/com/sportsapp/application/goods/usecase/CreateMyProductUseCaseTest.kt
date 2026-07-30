package com.sportsapp.application.goods.usecase

import com.sportsapp.domain.common.security.OwnershipGuard
import com.sportsapp.domain.goods.service.GoodsDomainService
import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.vo.ProductCategory
import com.sportsapp.domain.goods.entity.ProductStatus
import com.sportsapp.domain.goods.dto.ProductWithStock
import com.sportsapp.domain.goods.entity.Stock
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import com.sportsapp.application.goods.dto.CreateMyProductCommand

class CreateMyProductUseCaseTest : BehaviorSpec({

    val goodsDomainService = mockk<GoodsDomainService>()
    val ownershipGuard = mockk<OwnershipGuard>()
    val useCase = CreateMyProductUseCase(goodsDomainService, ownershipGuard)

    Given("GOODS_SELLER 인증 사용자가 상품 등록 요청을 보낼 때") {
        val ownerUserId = 42L
        val command = CreateMyProductCommand(
            name = "테니스 라켓",
            category = ProductCategory.EQUIPMENT,
            price = BigDecimal("50000"),
            description = "프리미엄 라켓",
            imageUrl = "https://example.com/racket.jpg",
        )

        val product = Product(
            name = command.name,
            category = command.category,
            price = command.price,
            description = command.description,
            imageUrl = command.imageUrl,
            status = ProductStatus.INACTIVE,
            ownerId = ownerUserId,
        )
        val stock = Stock(productId = 0L, quantity = 0)

        every { ownershipGuard.authUserId() } returns ownerUserId
        every {
            goodsDomainService.createProduct(
                name = command.name,
                category = command.category,
                price = command.price,
                description = command.description,
                imageUrl = command.imageUrl,
                ownerUserId = ownerUserId,
            )
        } returns (product to stock)

        When("[U-03] execute 호출하면") {
            val result = useCase.execute(command)

            Then("ownerId가 authUserId로 자동 주입되어 DomainService를 호출한다") {
                verify(exactly = 1) { ownershipGuard.authUserId() }
                verify(exactly = 1) {
                    goodsDomainService.createProduct(
                        name = command.name,
                        category = command.category,
                        price = command.price,
                        description = command.description,
                        imageUrl = command.imageUrl,
                        ownerUserId = ownerUserId,
                    )
                }
            }

            Then("status는 INACTIVE이고 stockQuantity는 0이다") {
                result.product.status shouldBe ProductStatus.INACTIVE
                result.stockQuantity shouldBe 0
            }
        }
    }
})
