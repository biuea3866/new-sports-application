package com.sportsapp.presentation.mcp.controller

import com.sportsapp.application.dashboard.dto.DashboardSummaryResponse
import com.sportsapp.application.dashboard.usecase.GetMyDashboardSummaryUseCase
import com.sportsapp.domain.mcp.vo.McpAuthenticatedPrincipal
import com.sportsapp.domain.mcp.vo.McpScope
import com.sportsapp.presentation.mcp.audit.McpAuditLogAsyncRecorder
import com.sportsapp.presentation.mcp.dto.response.McpResponseStatus
import com.sportsapp.presentation.mcp.controller.McpOperatorProfileTools
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

class McpOperatorProfileToolsTest : BehaviorSpec({

    val getMyDashboardSummaryUseCase = mockk<GetMyDashboardSummaryUseCase>()
    val mcpAuditLogAsyncRecorder = mockk<McpAuditLogAsyncRecorder>(relaxed = true)
    val mcpOperatorProfileTools = McpOperatorProfileTools(getMyDashboardSummaryUseCase, mcpAuditLogAsyncRecorder)

    val dashboardResponse = DashboardSummaryResponse(
        facilities = DashboardSummaryResponse.FacilitiesSummary(
            count = 3L,
            activeSlotsToday = 12L,
        ),
        events = null,
        products = null,
    )

    fun setupPrincipal(userId: Long) {
        val principal = object : McpAuthenticatedPrincipal {
            override val tokenId = 100L
            override val userId = userId
            override val grantedScopes = setOf<McpScope>()
        }
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(principal, null, emptyList())
    }

    afterEach {
        SecurityContextHolder.clearContext()
        clearMocks(mcpAuditLogAsyncRecorder)
    }

    Given("getOperatorProfile tool") {
        When("[U-10] 인증된 운영자(userId=42)로 getOperatorProfile을 호출하면") {
            setupPrincipal(42L)
            every { getMyDashboardSummaryUseCase.execute(42L) } returns dashboardResponse

            val result = mcpOperatorProfileTools.getOperatorProfile()

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
            setupPrincipal(99L)
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

            val result = mcpOperatorProfileTools.getOperatorProfile()

            Then("[U-11] facilities, events, products 요약이 모두 포함된다") {
                result.status shouldBe McpResponseStatus.OK
                val data = requireNotNull(result.data)
                data.facilities shouldNotBe null
                data.events shouldNotBe null
                data.products shouldNotBe null
            }
        }

        When("[U-12] getOperatorProfile을 호출하면 UseCase가 principal.userId를 인자로 호출된다") {
            setupPrincipal(42L)
            every { getMyDashboardSummaryUseCase.execute(42L) } returns dashboardResponse

            mcpOperatorProfileTools.getOperatorProfile()

            Then("[U-12] GetMyDashboardSummaryUseCase.execute(42L)가 호출된다") {
                verify { getMyDashboardSummaryUseCase.execute(42L) }
            }
        }

        When("[U-17] IDOR — principal이 없으면 (SecurityContext 비어있음)") {
            SecurityContextHolder.clearContext()
            val localUseCase = mockk<GetMyDashboardSummaryUseCase>()
            val localRecorder = mockk<McpAuditLogAsyncRecorder>(relaxed = true)
            val localTools = McpOperatorProfileTools(localUseCase, localRecorder)

            Then("[U-17] AccessDeniedException이 발생하고 UseCase는 호출되지 않는다") {
                shouldThrow<AccessDeniedException> {
                    localTools.getOperatorProfile()
                }
                verify(exactly = 0) { localUseCase.execute(any()) }
            }
        }

        When("[U-audit-02] getOperatorProfile 호출 시 audit recorder가 1회 호출된다") {
            setupPrincipal(42L)
            every { getMyDashboardSummaryUseCase.execute(42L) } returns dashboardResponse

            mcpOperatorProfileTools.getOperatorProfile()

            Then("[U-audit-02] mcpAuditLogAsyncRecorder.record가 정확히 1회 호출된다") {
                verify(exactly = 1) {
                    mcpAuditLogAsyncRecorder.record(any(), any(), any(), any(), any(), any(), any(), any(), any())
                }
            }
        }
    }
})
