package com.sportsapp.application.goods

import com.sportsapp.domain.goods.GoodsDomainService
import com.sportsapp.domain.goods.GoodsSalesSummary
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.time.ZonedDateTime

class GetGoodsSalesUseCaseTest : BehaviorSpec({

    val goodsDomainService = mockk<GoodsDomainService>()
    val useCase = GetGoodsSalesUseCase(goodsDomainService)

    Given("[U-01] 정상 매출 조회 조건") {
        val now = ZonedDateTime.now()
        val from = now.minusDays(30)
        val to = now.minusDays(1)

        val summaries = listOf(
            GoodsSalesSummary(productId = 1L, productName = "상품A", totalRevenue = BigDecimal("50000"), orderCount = 5L),
            GoodsSalesSummary(productId = 2L, productName = "상품B", totalRevenue = BigDecimal("30000"), orderCount = 3L),
        )
        every {
            goodsDomainService.aggregateSales(
                ownerUserId = 10L,
                productId = null,
                from = from,
                to = to,
            )
        } returns summaries

        When("[U-01] execute 호출 시") {
            val command = GetGoodsSalesCommand(operatorUserId = 10L, productId = null, from = from, to = to)
            val response = useCase.execute(command)

            Then("[U-01] DomainService 모킹 후 정상 매출 합계가 반환된다") {
                response.items.size shouldBe 2
                response.items[0].totalRevenue shouldBe BigDecimal("50000")
                response.items[1].orderCount shouldBe 3L
            }
        }
    }

    Given("[U-02] 미래 날짜 범위 입력") {
        val from = ZonedDateTime.now().plusDays(1)
        val to = ZonedDateTime.now().plusDays(7)

        When("[U-02] execute 호출 시") {
            val command = GetGoodsSalesCommand(operatorUserId = 10L, productId = null, from = from, to = to)

            Then("[U-02] 미래 날짜 범위 입력 시 도메인 예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    useCase.execute(command)
                }
            }
        }
    }
})
