package com.sportsapp.application.ticketing

import com.sportsapp.domain.ticketing.TicketSalesSummary
import com.sportsapp.domain.ticketing.TicketingDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.time.ZonedDateTime

class GetTicketSalesUseCaseTest : BehaviorSpec({

    val ticketingDomainService = mockk<TicketingDomainService>()
    val getTicketSalesUseCase = GetTicketSalesUseCase(ticketingDomainService)

    val from = ZonedDateTime.parse("2026-01-01T00:00:00+09:00")
    val to = ZonedDateTime.parse("2026-01-31T23:59:59+09:00")

    Given("[U-01] ownerUserId=1, 전체 이벤트 기간 조회 시 판매 10건, 매출 300000원, 취소 2건인 경우") {
        val command = GetTicketSalesCommand(ownerUserId = 1L, eventId = null, from = from, to = to)
        val summary = TicketSalesSummary(totalTicketCount = 10L, totalRevenue = BigDecimal("300000"), cancelledCount = 2L)
        every {
            ticketingDomainService.aggregateTicketSales(
                ownerUserId = 1L,
                eventId = null,
                from = from,
                to = to,
            )
        } returns summary

        When("execute를 호출하면") {
            val result = getTicketSalesUseCase.execute(command)

            Then("[U-01] 집계값이 올바르게 담긴 TicketSalesResponse가 반환된다") {
                result.ownerUserId shouldBe 1L
                result.totalTicketCount shouldBe 10L
                result.totalRevenue shouldBe BigDecimal("300000")
                result.cancelledCount shouldBe 2L
            }
        }
    }

    Given("[U-02] 해당 기간에 티켓 판매 내역이 없을 때") {
        val command = GetTicketSalesCommand(ownerUserId = 2L, eventId = null, from = from, to = to)
        val summary = TicketSalesSummary(totalTicketCount = 0L, totalRevenue = BigDecimal.ZERO, cancelledCount = 0L)
        every {
            ticketingDomainService.aggregateTicketSales(
                ownerUserId = 2L,
                eventId = null,
                from = from,
                to = to,
            )
        } returns summary

        When("execute를 호출하면") {
            val result = getTicketSalesUseCase.execute(command)

            Then("[U-02] 모든 집계값이 0인 TicketSalesResponse가 반환된다") {
                result.totalTicketCount shouldBe 0L
                result.totalRevenue shouldBe BigDecimal.ZERO
                result.cancelledCount shouldBe 0L
            }
        }
    }

    Given("[U-03] 특정 eventId=99로 필터링하여 조회할 때") {
        val command = GetTicketSalesCommand(ownerUserId = 1L, eventId = 99L, from = from, to = to)
        val summary = TicketSalesSummary(totalTicketCount = 5L, totalRevenue = BigDecimal("150000"), cancelledCount = 1L)
        every {
            ticketingDomainService.aggregateTicketSales(
                ownerUserId = 1L,
                eventId = 99L,
                from = from,
                to = to,
            )
        } returns summary

        When("execute를 호출하면") {
            val result = getTicketSalesUseCase.execute(command)

            Then("[U-03] eventId 필터가 적용된 집계값이 반환된다") {
                result.totalTicketCount shouldBe 5L
                result.totalRevenue shouldBe BigDecimal("150000")
                result.cancelledCount shouldBe 1L
            }
        }
    }
})
