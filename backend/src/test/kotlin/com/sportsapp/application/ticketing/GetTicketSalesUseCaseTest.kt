package com.sportsapp.application.ticketing

import com.sportsapp.domain.common.exceptions.UnauthorizedException
import com.sportsapp.domain.ticketing.Event
import com.sportsapp.domain.ticketing.EventStatus
import com.sportsapp.domain.ticketing.TicketSalesSummary
import com.sportsapp.domain.ticketing.TicketingDomainService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.time.ZonedDateTime

class GetTicketSalesUseCaseTest : BehaviorSpec({

    val ticketingDomainService = mockk<TicketingDomainService>()
    val useCase = GetTicketSalesUseCase(ticketingDomainService)

    val now = ZonedDateTime.now()
    val from = now.minusDays(30)
    val to = now.minusDays(1)

    Given("[U-04] 자기 이벤트에 대한 티켓 집계 조회") {
        val event = mockk<Event>(relaxed = true) {
            every { id } returns 100L
            every { ownerId } returns 5L
        }
        val summary = TicketSalesSummary(totalTicketCount = 50L, totalRevenue = BigDecimal("500000"), cancelledCount = 3L)

        every { ticketingDomainService.getEvent(100L) } returns event
        every { ticketingDomainService.aggregateTicketSales(5L, 100L, from, to) } returns summary

        When("[U-04] 자기 이벤트로 execute 호출 시") {
            val command = GetTicketSalesCommand(operatorUserId = 5L, eventId = 100L, from = from, to = to)
            val response = useCase.execute(command)

            Then("[U-04] 정상적으로 집계 결과가 반환된다") {
                response.totalTicketCount shouldBe 50L
                response.totalRevenue shouldBe BigDecimal("500000")
                response.cancelledCount shouldBe 3L
            }
        }
    }

    Given("[U-04b] 다른 운영자의 ticketing을 조회 시도") {
        val event = mockk<Event>(relaxed = true) {
            every { id } returns 200L
            every { ownerId } returns 99L
        }
        every { ticketingDomainService.getEvent(200L) } returns event

        When("[U-04b] 타인 이벤트로 execute 호출 시") {
            val command = GetTicketSalesCommand(operatorUserId = 5L, eventId = 200L, from = from, to = to)

            Then("[U-04] 다른 운영자의 ticketing을 조회 시도하면 예외가 발생한다") {
                shouldThrow<UnauthorizedException> {
                    useCase.execute(command)
                }
            }
        }
    }
})
