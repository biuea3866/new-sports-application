package com.sportsapp.application.ticketing

import com.sportsapp.domain.ticketing.Event
import com.sportsapp.domain.ticketing.EventCriteria
import com.sportsapp.domain.ticketing.EventStatus
import com.sportsapp.domain.ticketing.TicketingDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.ZoneOffset
import java.time.ZonedDateTime

class ListEventsUseCaseTest : BehaviorSpec({

    val ticketingDomainService = mockk<TicketingDomainService>()
    val listEventsUseCase = ListEventsUseCase(ticketingDomainService)

    val startsAt = ZonedDateTime.of(2026, 12, 1, 18, 0, 0, 0, ZoneOffset.UTC)

    fun buildEvent(id: Long, status: EventStatus) = Event(
        id = id,
        title = "Concert $id",
        venue = "Seoul Arena",
        startsAt = startsAt,
        status = status,
    )

    Given("OPEN 상태 이벤트 2건이 존재할 때") {
        val pageable = PageRequest.of(0, 10)
        val criteria = EventCriteria(status = EventStatus.OPEN, startsAtFrom = null, startsAtTo = null)
        val events = listOf(buildEvent(1L, EventStatus.OPEN), buildEvent(2L, EventStatus.OPEN))
        val page = PageImpl(events, pageable, 2L)

        every { ticketingDomainService.listEvents(criteria, pageable) } returns page

        When("[U-01] listEvents를 호출하면") {
            val result = listEventsUseCase.execute(criteria, pageable)

            Then("status=OPEN 이벤트 2건이 반환된다") {
                result.totalElements shouldBe 2L
                result.content.size shouldBe 2
                result.content.all { it.status == EventStatus.OPEN.name } shouldBe true
            }
        }
    }

    Given("날짜 범위 필터로 조회할 때") {
        val pageable = PageRequest.of(0, 10)
        val from = ZonedDateTime.of(2026, 11, 1, 0, 0, 0, 0, ZoneOffset.UTC)
        val to = ZonedDateTime.of(2026, 12, 31, 23, 59, 59, 0, ZoneOffset.UTC)
        val criteria = EventCriteria(status = null, startsAtFrom = from, startsAtTo = to)
        val events = listOf(buildEvent(1L, EventStatus.SCHEDULED))
        val page = PageImpl(events, pageable, 1L)

        every { ticketingDomainService.listEvents(criteria, pageable) } returns page

        When("[U-02] 날짜 범위에 속하는 이벤트를 조회하면") {
            val result = listEventsUseCase.execute(criteria, pageable)

            Then("범위 내 이벤트 1건이 반환된다") {
                result.totalElements shouldBe 1L
                result.content.first().title shouldBe "Concert 1"
            }
        }
    }
})
