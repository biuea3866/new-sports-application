package com.sportsapp.application.ticketing

import com.sportsapp.domain.ticketing.Event
import com.sportsapp.domain.ticketing.EventStatus
import com.sportsapp.domain.ticketing.SeatSpec
import com.sportsapp.domain.ticketing.TicketingDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.ZonedDateTime

class CreateMyEventUseCaseTest : BehaviorSpec({

    val ticketingDomainService = mockk<TicketingDomainService>()
    val createMyEventUseCase = CreateMyEventUseCase(ticketingDomainService)

    Given("유효한 이벤트 생성 커맨드가 주어졌을 때") {
        val startsAt = ZonedDateTime.now().plusDays(30)
        val command = CreateMyEventCommand(
            ownerUserId = 1L,
            title = "Summer Concert",
            venue = "Seoul Arena",
            startsAt = startsAt,
            seats = listOf(SeatSpecCommand("A", "1", "1", BigDecimal("50000"))),
        )
        val createdEvent = mockk<Event>(relaxed = true)
        every { createdEvent.id } returns 100L
        every { createdEvent.title } returns "Summer Concert"
        every { createdEvent.venue } returns "Seoul Arena"
        every { createdEvent.startsAt } returns startsAt
        every { createdEvent.status } returns EventStatus.SCHEDULED
        every { createdEvent.ownerId } returns 1L

        every {
            ticketingDomainService.createEvent(
                title = "Summer Concert",
                venue = "Seoul Arena",
                startsAt = startsAt,
                seats = listOf(SeatSpec("A", "1", "1", BigDecimal("50000"))),
                ownerUserId = 1L,
            )
        } returns createdEvent

        When("execute를 호출하면") {
            val response = createMyEventUseCase.execute(command)

            Then("[U-01] SCHEDULED 상태의 이벤트가 반환된다") {
                response.id shouldBe 100L
                response.title shouldBe "Summer Concert"
                response.status shouldBe "SCHEDULED"
                response.ownerId shouldBe 1L
                response.confirmedSeatCount shouldBe 0L
            }

            Then("[U-01] ticketingDomainService.createEvent가 1회 호출된다") {
                verify(exactly = 1) {
                    ticketingDomainService.createEvent(any(), any(), any(), any(), any())
                }
            }
        }
    }
})
