package com.sportsapp.presentation.mcp

import com.sportsapp.application.ticketing.GetTicketSalesUseCase
import com.sportsapp.application.ticketing.GetTicketSalesCommand
import com.sportsapp.application.ticketing.TicketSalesResponse
import com.sportsapp.presentation.mcp.response.McpResponseStatus
import com.sportsapp.presentation.mcp.toolregistry.McpTicketSalesTools
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.math.BigDecimal
import java.time.ZonedDateTime

class McpTicketSalesToolsTest : BehaviorSpec({

    val getTicketSalesUseCase = mockk<GetTicketSalesUseCase>()
    val mcpTicketSalesTools = McpTicketSalesTools(getTicketSalesUseCase)

    Given("getTicketSales tool") {
        val from = ZonedDateTime.now().minusDays(30)
        val to = ZonedDateTime.now()
        val salesResponse = TicketSalesResponse(
            ownerUserId = 10L,
            totalTicketCount = 100L,
            totalRevenue = BigDecimal("1500000.00"),
            cancelledCount = 5L,
        )

        When("[U-05] ownerUserId와 기간으로 getTicketSales를 호출하면") {
            val commandSlot = slot<GetTicketSalesCommand>()
            every { getTicketSalesUseCase.execute(capture(commandSlot)) } returns salesResponse

            val result = mcpTicketSalesTools.getTicketSales(
                ownerUserId = 10L,
                eventId = null,
                from = from.toString(),
                to = to.toString(),
            )

            Then("[U-05] OK 상태와 티켓 판매 통계가 반환된다") {
                result.status shouldBe McpResponseStatus.OK
                result.data shouldNotBe null
                val data = requireNotNull(result.data)
                data.ownerUserId shouldBe 10L
                data.totalTicketCount shouldBe 100L
                data.totalRevenue shouldBe BigDecimal("1500000.00")
                data.cancelledCount shouldBe 5L
            }
        }

        When("[U-06] eventId를 포함해 getTicketSales를 호출하면") {
            val commandSlot = slot<GetTicketSalesCommand>()
            every { getTicketSalesUseCase.execute(capture(commandSlot)) } returns salesResponse

            mcpTicketSalesTools.getTicketSales(
                ownerUserId = 10L,
                eventId = 42L,
                from = from.toString(),
                to = to.toString(),
            )

            Then("[U-06] command에 eventId가 포함되어 전달된다") {
                commandSlot.captured.ownerUserId shouldBe 10L
                commandSlot.captured.eventId shouldBe 42L
            }
        }

        When("[U-07] 판매 실적이 없는 기간으로 getTicketSales를 호출하면") {
            every { getTicketSalesUseCase.execute(any()) } returns TicketSalesResponse(
                ownerUserId = 99L,
                totalTicketCount = 0L,
                totalRevenue = BigDecimal.ZERO,
                cancelledCount = 0L,
            )

            val result = mcpTicketSalesTools.getTicketSales(
                ownerUserId = 99L,
                eventId = null,
                from = from.toString(),
                to = to.toString(),
            )

            Then("[U-07] OK 상태와 0 통계가 반환된다") {
                result.status shouldBe McpResponseStatus.OK
                val data = requireNotNull(result.data)
                data.totalTicketCount shouldBe 0L
                data.totalRevenue shouldBe BigDecimal.ZERO
            }
        }
    }
})
