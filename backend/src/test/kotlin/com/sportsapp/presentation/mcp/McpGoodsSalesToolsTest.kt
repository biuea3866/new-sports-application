package com.sportsapp.presentation.mcp

import com.sportsapp.application.goods.GetGoodsSalesUseCase
import com.sportsapp.application.goods.GoodsSalesResponse
import com.sportsapp.presentation.mcp.response.McpResponseStatus
import com.sportsapp.presentation.mcp.toolregistry.McpGoodsSalesTools
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.math.BigDecimal

class McpGoodsSalesToolsTest : BehaviorSpec({

    val getGoodsSalesUseCase = mockk<GetGoodsSalesUseCase>()
    val mcpGoodsSalesTools = McpGoodsSalesTools(getGoodsSalesUseCase)

    Given("getGoodsSales tool") {
        val salesResponse = GoodsSalesResponse(
            ownerUserId = 10L,
            activeProductCount = 5L,
            outOfStockProductCount = 2L,
            confirmedOrderCount = 20L,
            totalRevenue = BigDecimal("500000.00"),
        )

        When("[U-01] ownerUserId로 getGoodsSales를 호출하면") {
            val ownerUserIdSlot = slot<Long>()
            every { getGoodsSalesUseCase.execute(capture(ownerUserIdSlot)) } returns salesResponse

            val result = mcpGoodsSalesTools.getGoodsSales(ownerUserId = 10L)

            Then("[U-01] OK 상태와 판매 통계가 반환된다") {
                result.status shouldBe McpResponseStatus.OK
                result.data shouldNotBe null
                val data = requireNotNull(result.data)
                data.ownerUserId shouldBe 10L
                data.activeProductCount shouldBe 5L
                data.confirmedOrderCount shouldBe 20L
                data.totalRevenue shouldBe BigDecimal("500000.00")
            }
        }

        When("[U-02] 판매 실적이 없는 ownerUserId로 getGoodsSales를 호출하면") {
            every { getGoodsSalesUseCase.execute(99L) } returns GoodsSalesResponse(
                ownerUserId = 99L,
                activeProductCount = 0L,
                outOfStockProductCount = 0L,
                confirmedOrderCount = 0L,
                totalRevenue = BigDecimal.ZERO,
            )

            val result = mcpGoodsSalesTools.getGoodsSales(ownerUserId = 99L)

            Then("[U-02] OK 상태와 0 통계가 반환된다") {
                result.status shouldBe McpResponseStatus.OK
                val data = requireNotNull(result.data)
                data.activeProductCount shouldBe 0L
                data.totalRevenue shouldBe BigDecimal.ZERO
            }
        }
    }
})
