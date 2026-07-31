package com.sportsapp.domain.ticketing
import com.sportsapp.domain.ticketing.dto.TicketOrderResult
import com.sportsapp.domain.ticketing.entity.Event
import com.sportsapp.domain.ticketing.entity.OrderStatus
import com.sportsapp.domain.ticketing.entity.TicketOrder
import com.sportsapp.domain.ticketing.repository.EventRepository
import com.sportsapp.domain.ticketing.repository.TicketRepository
import com.sportsapp.domain.ticketing.repository.TicketOrderRepository
import com.sportsapp.domain.ticketing.gateway.SeatLockStore
import com.sportsapp.domain.ticketing.service.TicketingDomainService

import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.ticketing.exception.MalformedLockIdException
import com.sportsapp.domain.ticketing.exception.UnauthorizedTicketOrderAccessException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
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
        featureFlagEvaluator = mockk(relaxed = true),
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

    // confirmOrder의 CAS 전이(tryConfirm)·티켓 발급·멱등·상태 보호 회귀는
    // TicketOrderConfirmDomainServiceTest에서 전담 검증한다(W1-11b 요건 2).

    Given("존재하지 않는 orderId로 confirmOrder를 호출할 때") {
        val ticketOrderRepository = mockk<TicketOrderRepository>()
        val service = buildService(ticketOrderRepository = ticketOrderRepository)

        every { ticketOrderRepository.tryConfirm(orderId = 999L, paymentId = 1L) } returns false
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

        When("소유자(requesterId=1L)로 getTicketOrderDetail(100L)을 호출하면") {
            val result = service.getTicketOrderDetail(100L, requesterId = 1L)

            Then("이벤트명·이벤트id·결제id·생성일시가 채워진 TicketOrderDetail이 반환된다") {
                result.ticketOrderId shouldBe 100L
                result.status shouldBe OrderStatus.PENDING
                result.eventId shouldBe 7L
                result.eventTitle shouldBe "월드컵 결승"
                result.paymentId shouldBe 555L
                result.createdAt shouldBe createdAt
            }

            Then("소유자 검증(requireOwnedBy)이 requesterId로 호출된다") {
                verify(exactly = 1) { order.requireOwnedBy(1L) }
            }
        }
    }

    Given("TicketOrder가 존재하지만 요청자가 소유자가 아닐 때") {
        val ticketOrderRepository = mockk<TicketOrderRepository>()
        val eventRepository = mockk<EventRepository>()
        val service = buildService(
            ticketOrderRepository = ticketOrderRepository,
            eventRepository = eventRepository,
        )
        val order = mockk<TicketOrder>(relaxed = true).also {
            every { it.id } returns 100L
            every { it.requireOwnedBy(99L) } throws UnauthorizedTicketOrderAccessException(100L)
        }
        every { ticketOrderRepository.findById(100L) } returns order

        When("소유자가 아닌 requesterId=99L로 getTicketOrderDetail(100L)을 호출하면") {
            Then("UnauthorizedTicketOrderAccessException이 발생한다") {
                shouldThrow<UnauthorizedTicketOrderAccessException> {
                    service.getTicketOrderDetail(100L, requesterId = 99L)
                }
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

        When("소유자(requesterId=1L)로 getTicketOrderDetail(200L)을 호출하면") {
            val result = service.getTicketOrderDetail(200L, requesterId = 1L)

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
                    service.getTicketOrderDetail(999L, requesterId = 1L)
                }
            }
        }
    }
})
