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
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import com.sportsapp.application.goods.dto.RestoreMyProductStockCommand

class RestoreMyProductStockUseCaseTest : BehaviorSpec({

    val goodsDomainService = mockk<GoodsDomainService>()
    val ownershipGuard = mockk<OwnershipGuard>()
    val useCase = RestoreMyProductStockUseCase(goodsDomainService, ownershipGuard)

    Given("GOODS_SELLER 인증 사용자가 재고 보충 요청을 보낼 때") {
        val ownerUserId = 42L
        val productId = 10L
        val command = RestoreMyProductStockCommand(productId = productId, quantity = 10)

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
        justRun { goodsDomainService.restoreProductStock(productId, ownerUserId, 10) }
        every {
            goodsDomainService.getProductByIdAndOwnerId(productId, ownerUserId)
        } returns ProductWithStock(product = product, stockQuantity = 10)

        When("[U-02] execute 호출하면") {
            val result = useCase.execute(command)

            Then("OwnershipGuard를 경유하여 ownership 검증 후 재고 보충이 실행된다") {
                verify(exactly = 1) { ownershipGuard.authUserId() }
                verify(exactly = 1) { goodsDomainService.restoreProductStock(productId, ownerUserId, 10) }
            }

            Then("재고 보충 후 stockQuantity가 10이다") {
                result.stockQuantity shouldBe 10
            }
        }
    }
})
