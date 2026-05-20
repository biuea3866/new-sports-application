package com.sportsapp.application.ticketing

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.ticketing.Event
import com.sportsapp.domain.ticketing.EventStatus
import com.sportsapp.domain.ticketing.Seat
import com.sportsapp.domain.ticketing.TicketingDomainService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.time.ZoneOffset
import java.time.ZonedDateTime

class GetEventUseCaseTest : BehaviorSpec({

    val ticketingDomainService = mockk<TicketingDomainService>()
    val getEventUseCase = GetEventUseCase(ticketingDomainService)

    val startsAt = ZonedDateTime.of(2026, 12, 1, 18, 0, 0, 0, ZoneOffset.UTC)

    fun buildEvent(id: Long) = Event(
        id = id,
        title = "Concert $id",
        venue = "Seoul Arena",
        startsAt = startsAt,
        status = EventStatus.OPEN,
    )

    fun buildSeat(eventId: Long, section: String, seatNo: String) = Seat(
        id = 0L,
        eventId = eventId,
        section = section,
        rowNo = "1",
        seatNo = seatNo,
        price = BigDecimal("50000"),
    )

    Given("ID=42인 Event와 섹션별 좌석이 존재할 때") {
        val event = buildEvent(42L)
        val seats = listOf(
            buildSeat(42L, "A", "1"),
            buildSeat(42L, "A", "2"),
            buildSeat(42L, "B", "1"),
        )

        every { ticketingDomainService.getEvent(42L) } returns event
        every { ticketingDomainService.getSeats(42L) } returns seats

        When("[U-03] getEvent(42)를 호출하면") {
            val result = getEventUseCase.execute(42L)

            Then("섹션별 좌석 수가 포함된 EventDetailResponse가 반환된다") {
                result.id shouldBe 42L
                result.title shouldBe "Concert 42"
                result.startsAt shouldBe startsAt
                result.sections.size shouldBe 2
                result.sections.find { it.section == "A" }?.totalSeats shouldBe 2
                result.sections.find { it.section == "B" }?.totalSeats shouldBe 1
            }
        }
    }

    Given("존재하지 않는 Event ID를 요청할 때") {
        every { ticketingDomainService.getEvent(999L) } throws ResourceNotFoundException("Event", 999L)

        When("[U-02] getEvent(999)를 호출하면") {
            Then("ResourceNotFoundException이 발생한다") {
                shouldThrow<ResourceNotFoundException> {
                    getEventUseCase.execute(999L)
                }
            }
        }
    }
})
