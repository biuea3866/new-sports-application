package com.sportsapp.presentation.mcp

import com.sportsapp.application.ticketing.dto.GetTicketSalesCommand
import com.sportsapp.application.ticketing.usecase.GetTicketSalesUseCase
import com.sportsapp.application.ticketing.dto.TicketSalesResponse
import com.sportsapp.domain.mcp.McpAuthenticatedPrincipal
import com.sportsapp.domain.mcp.McpScope
import com.sportsapp.presentation.mcp.audit.McpAuditLogAsyncRecorder
import com.sportsapp.presentation.mcp.response.McpResponseStatus
import com.sportsapp.presentation.mcp.toolregistry.McpTicketSalesTools
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.math.BigDecimal
import java.time.ZonedDateTime

class McpTicketSalesToolsTest : BehaviorSpec({

    val getTicketSalesUseCase = mockk<GetTicketSalesUseCase>()
    val mcpAuditLogAsyncRecorder = mockk<McpAuditLogAsyncRecorder>(relaxed = true)
    val mcpTicketSalesTools = McpTicketSalesTools(getTicketSalesUseCase, mcpAuditLogAsyncRecorder)

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

    Given("getTicketSales tool") {
        val from = ZonedDateTime.now().minusDays(30)
        val to = ZonedDateTime.now()
        val salesResponse = TicketSalesResponse(
            ownerUserId = 10L,
            totalTicketCount = 100L,
            totalRevenue = BigDecimal("1500000.00"),
            cancelledCount = 5L,
        )

        When("[U-09] 인증된 운영자(userId=10)로 getTicketSales를 호출하면") {
            setupPrincipal(10L)
            every { getTicketSalesUseCase.execute(any()) } returns salesResponse

            val result = mcpTicketSalesTools.getTicketSales(
                eventId = null,
                from = from.toString(),
                to = to.toString(),
            )

            Then("[U-09] OK 상태와 티켓 판매 통계가 반환된다") {
                result.status shouldBe McpResponseStatus.OK
                result.data shouldNotBe null
                val data = requireNotNull(result.data)
                data.ownerUserId shouldBe 10L
                data.totalTicketCount shouldBe 100L
                data.totalRevenue shouldBe BigDecimal("1500000.00")
                data.cancelledCount shouldBe 5L
            }
        }

        When("[U-10] getTicketSales를 호출하면 command의 ownerUserId가 principal.userId와 일치한다") {
            setupPrincipal(10L)
            val commandSlot = slot<GetTicketSalesCommand>()
            every { getTicketSalesUseCase.execute(capture(commandSlot)) } returns salesResponse

            mcpTicketSalesTools.getTicketSales(
                eventId = null,
                from = from.toString(),
                to = to.toString(),
            )

            Then("[U-10] command.ownerUserId가 10이다") {
                commandSlot.captured.ownerUserId shouldBe 10L
            }
        }

        When("[U-11] eventId를 포함해 getTicketSales를 호출하면") {
            setupPrincipal(10L)
            val commandSlot = slot<GetTicketSalesCommand>()
            every { getTicketSalesUseCase.execute(capture(commandSlot)) } returns salesResponse

            mcpTicketSalesTools.getTicketSales(
                eventId = 42L,
                from = from.toString(),
                to = to.toString(),
            )

            Then("[U-11] command에 eventId가 포함되어 전달된다") {
                commandSlot.captured.ownerUserId shouldBe 10L
                commandSlot.captured.eventId shouldBe 42L
            }
        }

        When("[U-12] 판매 실적이 없는 기간으로 getTicketSales를 호출하면") {
            setupPrincipal(99L)
            every { getTicketSalesUseCase.execute(any()) } returns TicketSalesResponse(
                ownerUserId = 99L,
                totalTicketCount = 0L,
                totalRevenue = BigDecimal.ZERO,
                cancelledCount = 0L,
            )

            val result = mcpTicketSalesTools.getTicketSales(
                eventId = null,
                from = from.toString(),
                to = to.toString(),
            )

            Then("[U-12] OK 상태와 0 통계가 반환된다") {
                result.status shouldBe McpResponseStatus.OK
                val data = requireNotNull(result.data)
                data.totalTicketCount shouldBe 0L
                data.totalRevenue shouldBe BigDecimal.ZERO
            }
        }

        When("[U-13] IDOR — principal이 없으면 (SecurityContext 비어있음)") {
            SecurityContextHolder.clearContext()
            val localUseCase = mockk<GetTicketSalesUseCase>()
            val localRecorder = mockk<McpAuditLogAsyncRecorder>(relaxed = true)
            val localTools = McpTicketSalesTools(localUseCase, localRecorder)

            Then("[U-13] AccessDeniedException이 발생하고 UseCase는 호출되지 않는다") {
                shouldThrow<AccessDeniedException> {
                    localTools.getTicketSales(
                        eventId = null,
                        from = from.toString(),
                        to = to.toString(),
                    )
                }
                verify(exactly = 0) { localUseCase.execute(any()) }
            }
        }

        When("[U-audit-06] getTicketSales 호출 시 audit recorder가 1회 호출된다") {
            setupPrincipal(10L)
            every { getTicketSalesUseCase.execute(any()) } returns salesResponse

            mcpTicketSalesTools.getTicketSales(
                eventId = null,
                from = from.toString(),
                to = to.toString(),
            )

            Then("[U-audit-06] mcpAuditLogAsyncRecorder.record가 정확히 1회 호출된다") {
                verify(exactly = 1) {
                    mcpAuditLogAsyncRecorder.record(any(), any(), any(), any(), any(), any(), any(), any(), any())
                }
            }
        }
    }
})
