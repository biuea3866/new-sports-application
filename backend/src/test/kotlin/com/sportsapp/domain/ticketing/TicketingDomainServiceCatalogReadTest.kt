package com.sportsapp.domain.ticketing
import com.sportsapp.domain.ticketing.dto.EventCriteria
import com.sportsapp.domain.ticketing.dto.TicketOrderWithEventTitle
import com.sportsapp.domain.ticketing.entity.Event
import com.sportsapp.domain.ticketing.entity.EventStatus
import com.sportsapp.domain.ticketing.entity.OrderStatus
import com.sportsapp.domain.ticketing.gateway.SeatLockStore
import com.sportsapp.domain.ticketing.repository.EventCustomRepository
import com.sportsapp.domain.ticketing.repository.EventRepository
import com.sportsapp.domain.ticketing.repository.TicketOrderCustomRepository
import com.sportsapp.domain.ticketing.repository.TicketOrderRepository
import com.sportsapp.domain.ticketing.repository.TicketRepository
import com.sportsapp.domain.ticketing.service.TicketingDomainService

import com.sportsapp.domain.common.DomainEventPublisher
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.ZoneOffset
import java.time.ZonedDateTime

class TicketingDomainServiceCatalogReadTest : BehaviorSpec({

    fun buildService(
        eventCustomRepository: EventCustomRepository = mockk(relaxed = true),
        ticketOrderCustomRepository: TicketOrderCustomRepository = mockk(relaxed = true),
        eventRepository: EventRepository = mockk(relaxed = true),
        seatLockStore: SeatLockStore = mockk(relaxed = true),
        ticketOrderRepository: TicketOrderRepository = mockk(relaxed = true),
        ticketRepository: TicketRepository = mockk(relaxed = true),
        domainEventPublisher: DomainEventPublisher = mockk(relaxed = true),
    ) = TicketingDomainService(
        eventRepository = eventRepository,
        seatRepository = mockk(relaxed = true),
        eventCustomRepository = eventCustomRepository,
        seatCustomRepository = mockk(relaxed = true),
        ticketOrderCustomRepository = ticketOrderCustomRepository,
        seatLockStore = seatLockStore,
        ticketOrderRepository = ticketOrderRepository,
        ticketRepository = ticketRepository,
        domainEventPublisher = domainEventPublisher,
    )

    val startsAt = ZonedDateTime.of(2026, 12, 1, 18, 0, 0, 0, ZoneOffset.UTC)

    Given("keyword로 catalog 이벤트를 검색할 때") {
        val eventCustomRepository = mockk<EventCustomRepository>()
        val service = buildService(eventCustomRepository = eventCustomRepository)
        val pageable = PageRequest.of(0, 10)
        val event = Event.create("Rock Festival", "Seoul Arena", startsAt, 1L)
        val page: Page<Event> = PageImpl(listOf(event), pageable, 1L)
        val criteriaSlot = slot<EventCriteria>()

        every { eventCustomRepository.findByCriteria(capture(criteriaSlot), pageable) } returns page

        When("searchOpenEvents(keyword=\"Rock\", pageable)를 호출하면") {
            val result = service.searchOpenEvents(keyword = "Rock", pageable = pageable)

            Then("status=OPEN + keyword가 담긴 EventCriteria로 위임한다") {
                criteriaSlot.captured.status shouldBe EventStatus.OPEN
                criteriaSlot.captured.keyword shouldBe "Rock"
            }

            Then("조회된 이벤트 페이지가 그대로 반환된다") {
                result.totalElements shouldBe 1L
                result.content.first().title shouldBe "Rock Festival"
            }
        }
    }

    Given("keyword 없이 catalog 이벤트를 검색할 때") {
        val eventCustomRepository = mockk<EventCustomRepository>()
        val service = buildService(eventCustomRepository = eventCustomRepository)
        val pageable = PageRequest.of(0, 10)
        val page: Page<Event> = PageImpl(emptyList(), pageable, 0L)
        val criteriaSlot = slot<EventCriteria>()

        every { eventCustomRepository.findByCriteria(capture(criteriaSlot), pageable) } returns page

        When("searchOpenEvents(keyword=null, pageable)를 호출하면") {
            service.searchOpenEvents(keyword = null, pageable = pageable)

            Then("status=OPEN + keyword=null인 EventCriteria로 위임한다 (상태 보호, CLOSED/CANCELLED 제외)") {
                criteriaSlot.captured.status shouldBe EventStatus.OPEN
                criteriaSlot.captured.keyword shouldBe null
            }
        }
    }

    Given("사용자별 TicketOrder(이벤트명 포함) 목록을 조회할 때") {
        val ticketOrderCustomRepository = mockk<TicketOrderCustomRepository>()
        val service = buildService(ticketOrderCustomRepository = ticketOrderCustomRepository)
        val expected = listOf(
            TicketOrderWithEventTitle(
                ticketOrderId = 1L,
                status = OrderStatus.CONFIRMED,
                eventTitle = "Concert Dec",
                paymentId = 501L,
                createdAt = ZonedDateTime.of(2026, 6, 1, 10, 0, 0, 0, ZoneOffset.UTC),
            ),
        )
        every { ticketOrderCustomRepository.findBy(9L) } returns expected

        When("listTicketOrdersBy(9L)를 호출하면") {
            val result = service.listTicketOrdersBy(9L)

            Then("이벤트명이 포함된 주문 목록이 그대로 반환된다") {
                result shouldBe expected
            }
        }
    }
})
