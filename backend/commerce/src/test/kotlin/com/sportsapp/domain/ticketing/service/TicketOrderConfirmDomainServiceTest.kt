package com.sportsapp.domain.ticketing.service

import com.sportsapp.domain.common.DomainEvent
import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.common.FeatureFlagEvaluator
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.ticketing.entity.Event
import com.sportsapp.domain.ticketing.entity.OrderStatus
import com.sportsapp.domain.ticketing.entity.Ticket
import com.sportsapp.domain.ticketing.entity.TicketOrder
import com.sportsapp.domain.ticketing.entity.TicketStatus
import com.sportsapp.domain.ticketing.event.TicketEvent
import com.sportsapp.domain.ticketing.exception.InvalidOrderStateException
import com.sportsapp.domain.ticketing.gateway.SeatLockStore
import com.sportsapp.domain.ticketing.repository.EventCustomRepository
import com.sportsapp.domain.ticketing.repository.EventRepository
import com.sportsapp.domain.ticketing.repository.SeatCustomRepository
import com.sportsapp.domain.ticketing.repository.SeatRepository
import com.sportsapp.domain.ticketing.repository.TicketOrderCustomRepository
import com.sportsapp.domain.ticketing.repository.TicketOrderRepository
import com.sportsapp.domain.ticketing.repository.TicketRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.ZonedDateTime

/**
 * W1-11b 요건 2 — confirmOrder는 [TicketOrderRepository.tryConfirm] CAS(조건부 UPDATE, WHERE
 * status='PENDING')로 전이한다. 비잠금 findById → confirm() → save() 경로는 만료
 * 스위퍼([TicketingDomainService.expireTicketOrders])가 먼저 커밋한 CANCELLED를 조건 없는
 * dirty-checking UPDATE로 덮어쓰는 반대 방향 lost update(같은 좌석 이중 발권)를 만들 수 있어,
 * booking(W1-11c) `confirmBooking`과 대칭으로 CAS로 닫았다.
 */
class TicketOrderConfirmDomainServiceTest : BehaviorSpec({

    fun buildService(
        ticketOrderRepository: TicketOrderRepository = mockk(relaxed = true),
        ticketRepository: TicketRepository = mockk(relaxed = true),
        eventRepository: EventRepository = mockk(relaxed = true),
        domainEventPublisher: DomainEventPublisher = mockk(relaxed = true),
    ): TicketingDomainService = TicketingDomainService(
        eventRepository = eventRepository,
        seatRepository = mockk<SeatRepository>(relaxed = true),
        eventCustomRepository = mockk<EventCustomRepository>(relaxed = true),
        seatCustomRepository = mockk<SeatCustomRepository>(relaxed = true),
        ticketOrderCustomRepository = mockk<TicketOrderCustomRepository>(relaxed = true),
        seatLockStore = mockk<SeatLockStore>(relaxed = true),
        ticketOrderRepository = ticketOrderRepository,
        ticketRepository = ticketRepository,
        domainEventPublisher = domainEventPublisher,
        featureFlagEvaluator = mockk<FeatureFlagEvaluator>(relaxed = true),
    )

    Given("PENDING 상태의 TicketOrder를 confirmOrder로 확정할 때") {
        val ticketOrderRepository = mockk<TicketOrderRepository>()
        val ticketRepository = mockk<TicketRepository>()
        val eventRepository = mockk<EventRepository>()
        val domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val service = buildService(
            ticketOrderRepository = ticketOrderRepository,
            ticketRepository = ticketRepository,
            eventRepository = eventRepository,
            domainEventPublisher = domainEventPublisher,
        )

        // CAS 성공 후 재조회하면 CONFIRMED로 반영된 최신 행이 보인다(같은 트랜잭션·커넥션 내 가시성).
        val confirmedOrder = TicketOrder(
            userId = 7L,
            status = OrderStatus.CONFIRMED,
            paymentId = 999L,
            lockedEventId = 1L,
            lockedSeatIds = listOf(10L, 20L),
        )
        every { ticketOrderRepository.tryConfirm(orderId = 1L, paymentId = 999L) } returns true
        every { ticketOrderRepository.findById(1L) } returns confirmedOrder
        every { eventRepository.findById(1L) } returns Event.create("월드컵 결승", "상암 월드컵 경기장", ZonedDateTime.now(), 3L)
        val savedTicketsSlot = slot<List<Ticket>>()
        every { ticketRepository.saveAll(capture(savedTicketsSlot)) } answers { savedTicketsSlot.captured }
        val publishedSlot = slot<DomainEvent>()
        every { domainEventPublisher.publish(capture(publishedSlot)) } answers { Unit }

        When("confirmOrder(1L, 999L)를 호출하면") {
            val result = service.confirmOrder(1L, 999L)

            Then("CAS 전이가 성공해 status가 CONFIRMED로 반환된다") {
                result.status shouldBe OrderStatus.CONFIRMED
            }

            Then("잠긴 좌석마다 티켓이 발급된다") {
                val issuedTickets = savedTicketsSlot.captured
                issuedTickets.size shouldBe 2
                issuedTickets.map { it.seatId } shouldBe listOf(10L, 20L)
                issuedTickets.all { it.status == TicketStatus.ISSUED } shouldBe true
            }

            Then("수신자와 이벤트 제목을 담은 TicketEvent.Issued가 발행된다") {
                val published = publishedSlot.captured.shouldBeInstanceOf<TicketEvent.Issued>()
                published.recipientUserId shouldBe 7L
                published.eventTitle shouldBe "월드컵 결승"
            }
        }
    }

    Given("이미 CONFIRMED 상태인 TicketOrder에 confirmOrder를 재호출할 때 (webhook 중복 — 멱등)") {
        val ticketOrderRepository = mockk<TicketOrderRepository>()
        val ticketRepository = mockk<TicketRepository>()
        val domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val service = buildService(
            ticketOrderRepository = ticketOrderRepository,
            ticketRepository = ticketRepository,
            domainEventPublisher = domainEventPublisher,
        )

        val alreadyConfirmed = TicketOrder(
            userId = 1L, status = OrderStatus.CONFIRMED, paymentId = 100L, lockedEventId = 1L, lockedSeatIds = listOf(1L),
        )
        // CAS는 WHERE status='PENDING' 조건에 걸려 실패한다(이미 CONFIRMED).
        every { ticketOrderRepository.tryConfirm(orderId = 2L, paymentId = 200L) } returns false
        every { ticketOrderRepository.findById(2L) } returns alreadyConfirmed

        When("confirmOrder(2L, 200L)를 재호출하면") {
            val result = service.confirmOrder(2L, 200L)

            Then("멱등하게 처리되어 기존 상태가 유지되고 티켓이 재발급되지 않는다") {
                result.status shouldBe OrderStatus.CONFIRMED
                verify(exactly = 0) { ticketRepository.saveAll(any()) }
                verify(exactly = 0) { domainEventPublisher.publish(any()) }
            }
        }
    }

    Given("만료 스위퍼가 먼저 CANCELLED로 전이시킨 뒤 confirmOrder가 뒤늦게 도착할 때 (핵심 회귀 — 반대 방향 lost update·이중 발권 방지)") {
        val ticketOrderRepository = mockk<TicketOrderRepository>()
        val ticketRepository = mockk<TicketRepository>()
        val domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val service = buildService(
            ticketOrderRepository = ticketOrderRepository,
            ticketRepository = ticketRepository,
            domainEventPublisher = domainEventPublisher,
        )

        val cancelledOrder = TicketOrder(
            userId = 1L, status = OrderStatus.CANCELLED, paymentId = null, lockedEventId = 1L, lockedSeatIds = listOf(1L),
        )
        // CAS는 WHERE status='PENDING' 조건에 걸려 실패한다(이미 스위퍼가 CANCELLED로 전이함).
        every { ticketOrderRepository.tryConfirm(orderId = 4L, paymentId = 300L) } returns false
        every { ticketOrderRepository.findById(4L) } returns cancelledOrder

        When("confirmOrder(4L, 300L)를 호출하면") {
            Then("InvalidOrderStateException을 던져 CONFIRMED로 덮어쓰지 않는다 (같은 좌석 이중 발권 방지)") {
                val exception = shouldThrow<InvalidOrderStateException> {
                    service.confirmOrder(4L, 300L)
                }
                exception.message shouldContain "CANCELLED"
                verify(exactly = 0) { ticketRepository.saveAll(any()) }
                verify(exactly = 0) { domainEventPublisher.publish(any()) }
            }
        }
    }

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
})
