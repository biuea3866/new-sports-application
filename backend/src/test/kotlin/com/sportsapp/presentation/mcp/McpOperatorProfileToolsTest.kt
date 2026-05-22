package com.sportsapp.presentation.mcp

import com.sportsapp.application.dashboard.DashboardSummaryResponse
import com.sportsapp.application.dashboard.GetMyDashboardSummaryUseCase
import com.sportsapp.presentation.mcp.response.McpResponseStatus
import com.sportsapp.presentation.mcp.toolregistry.McpOperatorProfileTools
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class McpOperatorProfileToolsTest : BehaviorSpec({

    val getMyDashboardSummaryUseCase = mockk<GetMyDashboardSummaryUseCase>()
    val mcpOperatorProfileTools = McpOperatorProfileTools(getMyDashboardSummaryUseCase)

    Given("getOperatorProfile tool") {
        val dashboardResponse = DashboardSummaryResponse(
            facilities = DashboardSummaryResponse.FacilitiesSummary(
                count = 3L,
                activeSlotsToday = 12L,
            ),
            events = null,
            products = null,
        )

        When("[U-10] userId로 getOperatorProfile을 호출하면") {
            every { getMyDashboardSummaryUseCase.execute(42L) } returns dashboardResponse

            val result = mcpOperatorProfileTools.getOperatorProfile(userId = 42L)

            Then("[U-10] OK 상태와 운영자 대시보드 요약이 반환된다") {
                result.status shouldBe McpResponseStatus.OK
                result.data shouldNotBe null
                val data = requireNotNull(result.data)
                data.facilities shouldNotBe null
                data.facilities?.count shouldBe 3L
                data.facilities?.activeSlotsToday shouldBe 12L
                data.events shouldBe null
                data.products shouldBe null
            }
        }

        When("[U-11] 모든 역할을 보유한 운영자의 getOperatorProfile을 호출하면") {
            val fullResponse = DashboardSummaryResponse(
                facilities = DashboardSummaryResponse.FacilitiesSummary(count = 1L, activeSlotsToday = 5L),
                events = DashboardSummaryResponse.EventsSummary(
                    scheduled = 2L,
                    open = 1L,
                    closed = 3L,
                    totalSeats = 500L,
                    soldSeats = 300L,
                ),
                products = DashboardSummaryResponse.ProductsSummary(active = 10L, outOfStock = 2L),
            )
            every { getMyDashboardSummaryUseCase.execute(99L) } returns fullResponse

            val result = mcpOperatorProfileTools.getOperatorProfile(userId = 99L)

            Then("[U-11] facilities, events, products 요약이 모두 포함된다") {
                result.status shouldBe McpResponseStatus.OK
                val data = requireNotNull(result.data)
                data.facilities shouldNotBe null
                data.events shouldNotBe null
                data.products shouldNotBe null
            }
        }

        When("[U-12] getOperatorProfile을 호출하면 UseCase가 userId를 인자로 호출된다") {
            val localUseCase = mockk<GetMyDashboardSummaryUseCase>()
            val localTools = McpOperatorProfileTools(localUseCase)
            every { localUseCase.execute(42L) } returns dashboardResponse

            localTools.getOperatorProfile(userId = 42L)

            Then("[U-12] GetMyDashboardSummaryUseCase.execute(42L)가 호출된다") {
                verify { localUseCase.execute(42L) }
            }
        }
    }
})
