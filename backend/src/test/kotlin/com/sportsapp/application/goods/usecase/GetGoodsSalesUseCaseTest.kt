package com.sportsapp.application.goods.usecase

import com.sportsapp.domain.goods.service.GoodsDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal

class GetGoodsSalesUseCaseTest : BehaviorSpec({

    val goodsDomainService = mockk<GoodsDomainService>()
    val getGoodsSalesUseCase = GetGoodsSalesUseCase(goodsDomainService)

    Given("[U-01] ownerUserId=1인 판매자가 활성 상품 3개, 품절 상품 1개, 확정 주문 10건, 매출 50000원을 보유할 때") {
        every { goodsDomainService.countActiveProductsByOwnerId(1L) } returns 3L
        every { goodsDomainService.countOutOfStockProductsByOwnerId(1L) } returns 1L
        every { goodsDomainService.countConfirmedOrdersByOwnerUserId(1L) } returns 10L
        every { goodsDomainService.sumRevenueByOwnerUserId(1L) } returns BigDecimal("50000")

        When("execute를 호출하면") {
            val result = getGoodsSalesUseCase.execute(1L)

            Then("[U-01] 모든 집계값이 올바르게 담긴 GoodsSalesResponse가 반환된다") {
                result.ownerUserId shouldBe 1L
                result.activeProductCount shouldBe 3L
                result.outOfStockProductCount shouldBe 1L
                result.confirmedOrderCount shouldBe 10L
                result.totalRevenue shouldBe BigDecimal("50000")
            }
        }
    }

    Given("[U-02] ownerUserId=2인 판매자가 아무 데이터도 없을 때") {
        every { goodsDomainService.countActiveProductsByOwnerId(2L) } returns 0L
        every { goodsDomainService.countOutOfStockProductsByOwnerId(2L) } returns 0L
        every { goodsDomainService.countConfirmedOrdersByOwnerUserId(2L) } returns 0L
        every { goodsDomainService.sumRevenueByOwnerUserId(2L) } returns BigDecimal.ZERO

        When("execute를 호출하면") {
            val result = getGoodsSalesUseCase.execute(2L)

            Then("[U-02] 모든 집계값이 0인 GoodsSalesResponse가 반환된다") {
                result.activeProductCount shouldBe 0L
                result.outOfStockProductCount shouldBe 0L
                result.confirmedOrderCount shouldBe 0L
                result.totalRevenue shouldBe BigDecimal.ZERO
            }
        }
    }

    Given("[U-03] 매출이 Long.MAX_VALUE에 근접하는 큰 값일 때") {
        val largeRevenue = BigDecimal("9999999999999")
        every { goodsDomainService.countActiveProductsByOwnerId(3L) } returns Long.MAX_VALUE
        every { goodsDomainService.countOutOfStockProductsByOwnerId(3L) } returns Long.MAX_VALUE
        every { goodsDomainService.countConfirmedOrdersByOwnerUserId(3L) } returns Long.MAX_VALUE
        every { goodsDomainService.sumRevenueByOwnerUserId(3L) } returns largeRevenue

        When("execute를 호출하면") {
            val result = getGoodsSalesUseCase.execute(3L)

            Then("[U-03] 큰 값도 손실 없이 GoodsSalesResponse에 담긴다") {
                result.confirmedOrderCount shouldBe Long.MAX_VALUE
                result.totalRevenue shouldBe largeRevenue
            }
        }
    }
})
