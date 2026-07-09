package com.sportsapp.domain.ticketing
import com.sportsapp.domain.ticketing.dto.TicketOrderResult
import com.sportsapp.domain.ticketing.entity.Event
import com.sportsapp.domain.ticketing.entity.TicketStatus
import com.sportsapp.domain.ticketing.entity.OrderStatus
import com.sportsapp.domain.ticketing.entity.TicketOrder
import com.sportsapp.domain.ticketing.event.TicketEvent
import com.sportsapp.domain.ticketing.repository.EventRepository
import com.sportsapp.domain.ticketing.repository.TicketRepository
import com.sportsapp.domain.ticketing.repository.TicketOrderRepository
import com.sportsapp.domain.ticketing.gateway.SeatLockStore
import com.sportsapp.domain.ticketing.service.TicketingDomainService

import com.sportsapp.domain.common.DomainEvent
import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.ticketing.exception.MalformedLockIdException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.time.ZonedDateTime

class TicketingDomainServiceOrderTest : BehaviorSpec({

    fun buildService(
        seatLockStore: SeatLockStore = mockk(relaxed = true),
        ticketOrderRepository: TicketOrderRepository = mockk(relaxed = true),
        ticketRepository: TicketRepository = mockk(relaxed = true),
        domainEventPublisher: DomainEventPublisher = mockk(relaxed = true),
        eventRepository: EventRepository = mockk(relaxed = true),
    ) = TicketingDomainService(
        eventRepository = eventRepository,
        seatRepository = mockk(relaxed = true),
        eventCustomRepository = mockk(relaxed = true),
        seatCustomRepository = mockk(relaxed = true),
        ticketOrderCustomRepository = mockk(relaxed = true),
        seatLockStore = seatLockStore,
        ticketOrderRepository = ticketOrderRepository,
        ticketRepository = ticketRepository,
        domainEventPublisher = domainEventPublisher,
    )

    Given("유효한 lockId와 userId로 createPendingOrder를 호출할 때") {
        val ticketOrderRepository = mockk<TicketOrderRepository>()
        val savedOrderSlot = slot<TicketOrder>()
        val service = buildService(ticketOrderRepository = ticketOrderRepository)

        every { ticketOrderRepository.save(capture(savedOrderSlot)) } answers {
            savedOrderSlot.captured
        }

        When("createPendingOrder를 호출하면") {
            val result: TicketOrderResult = service.createPendingOrder("1:10,1:20", userId = 7L)

            Then("TicketOrderResult가 반환되고 status가 PENDING이다") {
                result.shouldBeInstanceOf<TicketOrderResult>()
                result.status shouldBe OrderStatus.PENDING
            }
        }
    }

    Given("잘못된 형식의 lockId로 createPendingOrder를 호출할 때") {
        val service = buildService()

        When("non-numeric lockId 토큰으로 호출하면") {
            Then("MalformedLockIdException이 발생한다") {
                shouldThrow<MalformedLockIdException> {
                    service.createPendingOrder("abc:xyz", userId = 1L)
                }
            }
        }
    }

    Given("존재하는 orderId와 paymentId로 confirmOrder를 호출할 때") {
        val ticketOrderRepository = mockk<TicketOrderRepository>()
        val ticketRepository = mockk<TicketRepository>()
        val domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val eventRepository = mockk<EventRepository>()
        val service = buildService(
            ticketOrderRepository = ticketOrderRepository,
            ticketRepository = ticketRepository,
            domainEventPublisher = domainEventPublisher,
            eventRepository = eventRepository,
        )

        val pendingOrder = TicketOrder(
            userId = 7L,
            status = OrderStatus.PENDING,
            paymentId = null,
            lockedEventId = 1L,
            lockedSeatIds = listOf(10L, 20L),
        )
        every { ticketOrderRepository.findById(100L) } returns pendingOrder
        every { ticketOrderRepository.save(any()) } returns pendingOrder
        every { eventRepository.findById(1L) } returns
            Event.create("월드컵 결승", "상암 월드컵 경기장", ZonedDateTime.now(), 3L)

        val publishedSlot = slot<DomainEvent>()
        every { domainEventPublisher.publish(capture(publishedSlot)) } answers { Unit }

        When("confirmOrder(100L, 999L)를 호출하면") {
            val result: TicketOrderResult = service.confirmOrder(100L, 999L)

            Then("TicketOrderResult가 반환되고 status가 CONFIRMED이다") {
                result.shouldBeInstanceOf<TicketOrderResult>()
                result.status shouldBe OrderStatus.CONFIRMED
            }

            Then("수신자와 이벤트 제목을 담은 TicketEvent.Issued가 발행된다") {
                val published = publishedSlot.captured.shouldBeInstanceOf<TicketEvent.Issued>()
                published.recipientUserId shouldBe 7L
                published.eventTitle shouldBe "월드컵 결승"
            }
        }
    }

    Given("존재하지 않는 orderId로 confirmOrder를 호출할 때") {
        val ticketOrderRepository = mockk<TicketOrderRepository>()
        val service = buildService(ticketOrderRepository = ticketOrderRepository)

        every { ticketOrderRepository.findById(999L) } returns null

        When("confirmOrder(999L, 1L)를 호출하면") {
            Then("ResourceNotFoundException이 발생한다") {
                shouldThrow<ResourceNotFoundException> {
                    service.confirmOrder(999L, 1L)
                }
            }
        }
    }

    Given("TicketOrder가 존재하고 참조 Event도 존재할 때") {
        val ticketOrderRepository = mockk<TicketOrderRepository>()
        val eventRepository = mockk<EventRepository>()
        val service = buildService(
            ticketOrderRepository = ticketOrderRepository,
            eventRepository = eventRepository,
        )
        val createdAt = ZonedDateTime.now()
        val order = mockk<TicketOrder>(relaxed = true).also {
            every { it.id } returns 100L
            every { it.status } returns OrderStatus.PENDING
            every { it.paymentId } returns 555L
            every { it.lockedEventId } returns 7L
            every { it.createdAt } returns createdAt
        }
        every { ticketOrderRepository.findById(100L) } returns order
        every { eventRepository.findById(7L) } returns
            Event.create("월드컵 결승", "상암 월드컵 경기장", ZonedDateTime.now(), 3L)

        When("getTicketOrderDetail(100L)을 호출하면") {
            val result = service.getTicketOrderDetail(100L)

            Then("이벤트명·이벤트id·결제id·생성일시가 채워진 TicketOrderDetail이 반환된다") {
                result.ticketOrderId shouldBe 100L
                result.status shouldBe OrderStatus.PENDING
                result.eventId shouldBe 7L
                result.eventTitle shouldBe "월드컵 결승"
                result.paymentId shouldBe 555L
                result.createdAt shouldBe createdAt
            }
        }
    }

    Given("TicketOrder는 존재하지만 참조 Event가 삭제되어 조회되지 않을 때") {
        val ticketOrderRepository = mockk<TicketOrderRepository>()
        val eventRepository = mockk<EventRepository>()
        val service = buildService(
            ticketOrderRepository = ticketOrderRepository,
            eventRepository = eventRepository,
        )
        val order = mockk<TicketOrder>(relaxed = true).also {
            every { it.id } returns 200L
            every { it.status } returns OrderStatus.CONFIRMED
            every { it.paymentId } returns 999L
            every { it.lockedEventId } returns 8L
            every { it.createdAt } returns ZonedDateTime.now()
        }
        every { ticketOrderRepository.findById(200L) } returns order
        every { eventRepository.findById(8L) } returns null

        When("getTicketOrderDetail(200L)을 호출하면") {
            val result = service.getTicketOrderDetail(200L)

            Then("eventTitle은 빈 문자열로 방어되고 eventId는 유지된다") {
                result.eventTitle shouldBe ""
                result.eventId shouldBe 8L
            }
        }
    }

    Given("존재하지 않는 ticketOrderId로 getTicketOrderDetail을 호출할 때") {
        val ticketOrderRepository = mockk<TicketOrderRepository>()
        val service = buildService(ticketOrderRepository = ticketOrderRepository)

        every { ticketOrderRepository.findById(999L) } returns null

        When("getTicketOrderDetail(999L)을 호출하면") {
            Then("ResourceNotFoundException이 발생한다") {
                shouldThrow<ResourceNotFoundException> {
                    service.getTicketOrderDetail(999L)
                }
            }
        }
    }
})
