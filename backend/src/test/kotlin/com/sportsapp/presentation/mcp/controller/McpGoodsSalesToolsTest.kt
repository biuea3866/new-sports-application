package com.sportsapp.presentation.mcp.controller

import com.sportsapp.application.goods.usecase.GetGoodsSalesUseCase
import com.sportsapp.application.goods.dto.GoodsSalesResult
import com.sportsapp.domain.mcp.vo.McpAuthenticatedPrincipal
import com.sportsapp.domain.mcp.vo.McpScope
import com.sportsapp.presentation.mcp.audit.McpAuditLogAsyncRecorder
import com.sportsapp.presentation.mcp.dto.response.McpResponseStatus
import com.sportsapp.presentation.mcp.controller.McpGoodsSalesTools
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
import java.math.BigDecimal

class McpGoodsSalesToolsTest : BehaviorSpec({

    val getGoodsSalesUseCase = mockk<GetGoodsSalesUseCase>()
    val mcpAuditLogAsyncRecorder = mockk<McpAuditLogAsyncRecorder>(relaxed = true)
    val mcpGoodsSalesTools = McpGoodsSalesTools(getGoodsSalesUseCase, mcpAuditLogAsyncRecorder)

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

    Given("getGoodsSales tool") {
        val salesResponse = GoodsSalesResult(
            ownerUserId = 10L,
            activeProductCount = 5L,
            outOfStockProductCount = 2L,
            confirmedOrderCount = 20L,
            totalRevenue = BigDecimal("500000.00"),
        )

        When("[U-01] 인증된 운영자(userId=10)로 getGoodsSales를 호출하면") {
            setupPrincipal(10L)
            every { getGoodsSalesUseCase.execute(10L) } returns salesResponse

            val result = mcpGoodsSalesTools.getGoodsSales()

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

        When("[U-02] getGoodsSales를 호출하면 UseCase가 principal.userId를 인자로 호출된다") {
            setupPrincipal(10L)
            every { getGoodsSalesUseCase.execute(10L) } returns salesResponse

            mcpGoodsSalesTools.getGoodsSales()

            Then("[U-02] GetGoodsSalesUseCase.execute(10L)가 호출된다") {
                verify { getGoodsSalesUseCase.execute(10L) }
            }
        }

        When("[U-03] 판매 실적이 없는 운영자(userId=99)로 getGoodsSales를 호출하면") {
            setupPrincipal(99L)
            every { getGoodsSalesUseCase.execute(99L) } returns GoodsSalesResult(
                ownerUserId = 99L,
                activeProductCount = 0L,
                outOfStockProductCount = 0L,
                confirmedOrderCount = 0L,
                totalRevenue = BigDecimal.ZERO,
            )

            val result = mcpGoodsSalesTools.getGoodsSales()

            Then("[U-03] OK 상태와 0 통계가 반환된다") {
                result.status shouldBe McpResponseStatus.OK
                val data = requireNotNull(result.data)
                data.activeProductCount shouldBe 0L
                data.totalRevenue shouldBe BigDecimal.ZERO
            }
        }

        When("[U-04] IDOR — principal이 없으면 (SecurityContext 비어있음)") {
            SecurityContextHolder.clearContext()
            val localUseCase = mockk<GetGoodsSalesUseCase>()
            val localRecorder = mockk<McpAuditLogAsyncRecorder>(relaxed = true)
            val localTools = McpGoodsSalesTools(localUseCase, localRecorder)

            Then("[U-04] AccessDeniedException이 발생하고 UseCase는 호출되지 않는다") {
                shouldThrow<AccessDeniedException> {
                    localTools.getGoodsSales()
                }
                verify(exactly = 0) { localUseCase.execute(any()) }
            }
        }

        When("[U-audit-04] getGoodsSales 호출 시 audit recorder가 1회 호출된다") {
            setupPrincipal(10L)
            every { getGoodsSalesUseCase.execute(10L) } returns salesResponse

            mcpGoodsSalesTools.getGoodsSales()

            Then("[U-audit-04] mcpAuditLogAsyncRecorder.record가 정확히 1회 호출된다") {
                verify(exactly = 1) {
                    mcpAuditLogAsyncRecorder.record(any(), any(), any(), any(), any(), any(), any(), any(), any())
                }
            }
        }
    }
})
