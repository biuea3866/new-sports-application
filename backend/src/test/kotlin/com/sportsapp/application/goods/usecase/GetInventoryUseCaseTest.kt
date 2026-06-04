package com.sportsapp.application.goods.usecase

import com.sportsapp.domain.goods.service.GoodsDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

class GetInventoryUseCaseTest : BehaviorSpec({

    val goodsDomainService = mockk<GoodsDomainService>()
    val getInventoryUseCase = GetInventoryUseCase(goodsDomainService)

    Given("[U-01] ownerUserId=1인 판매자가 활성 상품 5개, 품절 상품 2개를 보유할 때") {
        every { goodsDomainService.countActiveProductsByOwnerId(1L) } returns 5L
        every { goodsDomainService.countOutOfStockProductsByOwnerId(1L) } returns 2L

        When("execute를 호출하면") {
            val result = getInventoryUseCase.execute(1L)

            Then("[U-01] 활성/품절 재고 카운트가 담긴 InventoryResponse가 반환된다") {
                result.ownerUserId shouldBe 1L
                result.activeProductCount shouldBe 5L
                result.outOfStockProductCount shouldBe 2L
            }
        }
    }

    Given("[U-02] ownerUserId=2인 판매자가 아무 상품도 없을 때") {
        every { goodsDomainService.countActiveProductsByOwnerId(2L) } returns 0L
        every { goodsDomainService.countOutOfStockProductsByOwnerId(2L) } returns 0L

        When("execute를 호출하면") {
            val result = getInventoryUseCase.execute(2L)

            Then("[U-02] 모든 카운트가 0인 InventoryResponse가 반환된다") {
                result.activeProductCount shouldBe 0L
                result.outOfStockProductCount shouldBe 0L
            }
        }
    }

    Given("[U-03] 품절 상품 수가 활성 상품 수보다 많을 때") {
        every { goodsDomainService.countActiveProductsByOwnerId(3L) } returns 1L
        every { goodsDomainService.countOutOfStockProductsByOwnerId(3L) } returns 10L

        When("execute를 호출하면") {
            val result = getInventoryUseCase.execute(3L)

            Then("[U-03] 두 값이 각각 독립적으로 반환된다") {
                result.activeProductCount shouldBe 1L
                result.outOfStockProductCount shouldBe 10L
            }
        }
    }
})
