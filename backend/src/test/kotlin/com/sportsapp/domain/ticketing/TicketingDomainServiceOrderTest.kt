package com.sportsapp.domain.ticketing
import com.sportsapp.domain.ticketing.dto.TicketOrderResult
import com.sportsapp.domain.ticketing.entity.TicketStatus
import com.sportsapp.domain.ticketing.entity.OrderStatus
import com.sportsapp.domain.ticketing.entity.TicketOrder
import com.sportsapp.domain.ticketing.repository.TicketRepository
import com.sportsapp.domain.ticketing.repository.TicketOrderRepository
import com.sportsapp.domain.ticketing.gateway.SeatLockStore
import com.sportsapp.domain.ticketing.service.TicketingDomainService

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

class TicketingDomainServiceOrderTest : BehaviorSpec({

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
        val service = buildService(
            ticketOrderRepository = ticketOrderRepository,
            ticketRepository = ticketRepository,
            domainEventPublisher = domainEventPublisher,
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

        When("confirmOrder(100L, 999L)를 호출하면") {
            val result: TicketOrderResult = service.confirmOrder(100L, 999L)

            Then("TicketOrderResult가 반환되고 status가 CONFIRMED이다") {
                result.shouldBeInstanceOf<TicketOrderResult>()
                result.status shouldBe OrderStatus.CONFIRMED
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
})
