package com.sportsapp.application.ticketing

import com.sportsapp.domain.ticketing.Event
import com.sportsapp.domain.ticketing.EventStatus
import com.sportsapp.domain.ticketing.TicketingDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.ZonedDateTime

class ListMyEventsUseCaseTest : BehaviorSpec({

    val ticketingDomainService = mockk<TicketingDomainService>()
    val listMyEventsUseCase = ListMyEventsUseCase(ticketingDomainService)

    Given("ownerUserId=1에 이벤트 2건이 존재하고 각각 판매 카운트가 다를 때") {
        val pageable = PageRequest.of(0, 20)
        val now = ZonedDateTime.now()

        val event1 = mockk<Event>(relaxed = true)
        every { event1.id } returns 10L
        every { event1.title } returns "Event A"
        every { event1.venue } returns "Venue A"
        every { event1.startsAt } returns now.plusDays(10)
        every { event1.status } returns EventStatus.OPEN
        every { event1.ownerId } returns 1L

        val event2 = mockk<Event>(relaxed = true)
        every { event2.id } returns 11L
        every { event2.title } returns "Event B"
        every { event2.venue } returns "Venue B"
        every { event2.startsAt } returns now.plusDays(20)
        every { event2.status } returns EventStatus.SCHEDULED
        every { event2.ownerId } returns 1L

        every { ticketingDomainService.listMyEvents(1L, pageable) } returns
            PageImpl(listOf(event1, event2), pageable, 2L)
        every { ticketingDomainService.countConfirmedOrdersByEventId(10L) } returns 50L
        every { ticketingDomainService.countConfirmedOrdersByEventId(11L) } returns 0L

        When("execute를 호출하면") {
            val result = listMyEventsUseCase.execute(1L, pageable)

            Then("[U-02] 2건이 반환되며 각 이벤트의 confirmedSeatCount가 올바르게 매핑된다") {
                result.totalElements shouldBe 2L
                result.content[0].id shouldBe 10L
                result.content[0].confirmedSeatCount shouldBe 50L
                result.content[1].id shouldBe 11L
                result.content[1].confirmedSeatCount shouldBe 0L
            }
        }
    }
})
