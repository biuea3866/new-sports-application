package com.sportsapp.domain.ticketing

import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify

class TicketingDomainServiceCancelTest : BehaviorSpec({

    fun buildService(
        seatLockStore: SeatLockStore = mockk(relaxed = true),
        ticketOrderRepository: TicketOrderRepository = mockk(relaxed = true),
        ticketRepository: TicketRepository = mockk(relaxed = true),
        domainEventPublisher: DomainEventPublisher = mockk(relaxed = true),
    ) = TicketingDomainService(
        eventRepository = mockk(relaxed = true),
        seatRepository = mockk(relaxed = true),
        eventCustomRepository = mockk(relaxed = true),
        seatCustomRepository = mockk(relaxed = true),
        ticketOrderCustomRepository = mockk(relaxed = true),
        seatLockStore = seatLockStore,
        ticketOrderRepository = ticketOrderRepository,
        ticketRepository = ticketRepository,
        domainEventPublisher = domainEventPublisher,
    )

    fun buildPendingOrder(seatIds: List<Long> = listOf(101L)) = TicketOrder(
        userId = 7L,
        status = OrderStatus.PENDING,
        paymentId = null,
        lockedEventId = 10L,
        lockedSeatIds = seatIds,
    )

    fun buildIssuedTicket(order: TicketOrder, seatId: Long = 101L) = Ticket(
        ticketOrder = order,
        seatId = seatId,
        status = TicketStatus.ISSUED,
        code = "a".repeat(64),
    )

    Given("PENDING 주문과 ISSUED Ticket이 존재할 때 cancelOrder를 호출하면") {
        val ticketOrderRepository = mockk<TicketOrderRepository>()
        val ticketRepository = mockk<TicketRepository>()
        val seatLockStore = mockk<SeatLockStore>(relaxed = true)
        val service = buildService(
            seatLockStore = seatLockStore,
            ticketOrderRepository = ticketOrderRepository,
            ticketRepository = ticketRepository,
        )

        val order = buildPendingOrder()
        val ticket = buildIssuedTicket(order)
        val savedTicketsSlot = slot<List<Ticket>>()

        every { ticketOrderRepository.findById(1L) } returns order
        every { ticketOrderRepository.save(any()) } returns order
        every { ticketRepository.findByTicketOrderId(1L) } returns listOf(ticket)
        every { ticketRepository.saveAll(capture(savedTicketsSlot)) } returns listOf(ticket)

        When("cancelOrder(1L)을 호출하면") {
            service.cancelOrder(1L)

            Then("TicketOrder 상태가 CANCELLED로 전이된다") {
                order.status shouldBe OrderStatus.CANCELLED
            }

            Then("Ticket 상태가 REVOKED로 전이된다") {
                ticket.status shouldBe TicketStatus.REVOKED
            }

            Then("Ticket의 deletedAt이 설정되어 고아가 되지 않는다") {
                ticket.deletedAt shouldNotBe null
            }

            Then("saveAll이 호출되어 Ticket 변경이 DB에 반영된다") {
                verify(exactly = 1) { ticketRepository.saveAll(any()) }
            }
        }
    }

    Given("취소할 주문에 Ticket이 없을 때") {
        val ticketOrderRepository = mockk<TicketOrderRepository>()
        val ticketRepository = mockk<TicketRepository>()
        val service = buildService(
            ticketOrderRepository = ticketOrderRepository,
            ticketRepository = ticketRepository,
        )

        val order = buildPendingOrder()

        every { ticketOrderRepository.findById(1L) } returns order
        every { ticketOrderRepository.save(any()) } returns order
        every { ticketRepository.findByTicketOrderId(1L) } returns emptyList()

        When("cancelOrder(1L)을 호출하면") {
            service.cancelOrder(1L)

            Then("TicketOrder 상태가 CANCELLED로 전이된다") {
                order.status shouldBe OrderStatus.CANCELLED
            }

            Then("saveAll이 호출되지 않는다") {
                verify(exactly = 0) { ticketRepository.saveAll(any()) }
            }
        }
    }

    Given("존재하지 않는 orderId로 cancelOrder를 호출할 때") {
        val ticketOrderRepository = mockk<TicketOrderRepository>()
        val service = buildService(ticketOrderRepository = ticketOrderRepository)

        every { ticketOrderRepository.findById(999L) } returns null

        When("cancelOrder(999L)을 호출하면") {
            Then("ResourceNotFoundException이 발생한다") {
                shouldThrow<ResourceNotFoundException> {
                    service.cancelOrder(999L)
                }
            }
        }
    }
})
