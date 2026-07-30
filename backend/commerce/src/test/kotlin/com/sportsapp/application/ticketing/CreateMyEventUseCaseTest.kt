package com.sportsapp.application.ticketing
import com.sportsapp.application.ticketing.dto.CreateMyEventCommand
import com.sportsapp.application.ticketing.usecase.CreateMyEventUseCase

import com.sportsapp.domain.ticketing.entity.Event
import com.sportsapp.domain.ticketing.entity.EventStatus
import com.sportsapp.domain.ticketing.service.SeatSpec
import com.sportsapp.domain.ticketing.service.TicketingDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.ZoneOffset
import java.time.ZonedDateTime

class CreateMyEventUseCaseTest : BehaviorSpec({

    val ticketingDomainService = mockk<TicketingDomainService>()
    val createMyEventUseCase = CreateMyEventUseCase(ticketingDomainService)

    val startsAt = ZonedDateTime.of(2027, 3, 1, 18, 0, 0, 0, ZoneOffset.UTC)
    val ownerUserId = 10L

    fun buildEvent() = Event(
        id = 1L,
        title = "New Event",
        venue = "Arena",
        startsAt = startsAt,
        status = EventStatus.SCHEDULED,
        ownerId = ownerUserId,
    )

    fun buildSeatSpecs(count: Int): List<SeatSpec> =
        (1..count).map { index ->
            SeatSpec(
                section = "A",
                rowNo = ((index - 1) / 10 + 1).toString(),
                seatNo = ((index - 1) % 10 + 1).toString(),
                price = BigDecimal("50000"),
            )
        }

    Given("유효한 CreateMyEventCommand (좌석 100개)") {
        val seatSpecs = buildSeatSpecs(100)
        val command = CreateMyEventCommand(
            title = "New Event",
            venue = "Arena",
            startsAt = startsAt,
            seats = seatSpecs,
            ownerUserId = ownerUserId,
        )
        val savedEvent = buildEvent()

        every {
            ticketingDomainService.createEvent(
                title = command.title,
                venue = command.venue,
                startsAt = command.startsAt,
                seats = command.seats,
                ownerUserId = command.ownerUserId,
            )
        } returns savedEvent

        When("[U-03] execute(command)를 호출하면") {
            val result = createMyEventUseCase.execute(command)

            Then("TicketingDomainService.createEvent에 ownerUserId가 전달된다") {
                verify(exactly = 1) {
                    ticketingDomainService.createEvent(
                        title = command.title,
                        venue = command.venue,
                        startsAt = command.startsAt,
                        seats = command.seats,
                        ownerUserId = ownerUserId,
                    )
                }
                result.eventId shouldBe 1L
                result.seatCount shouldBe 100
            }
        }
    }
})
