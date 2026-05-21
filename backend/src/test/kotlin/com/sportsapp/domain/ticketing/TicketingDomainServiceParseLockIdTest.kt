package com.sportsapp.domain.ticketing

import com.sportsapp.domain.ticketing.exception.MalformedLockIdException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class TicketingDomainServiceParseLockIdTest : BehaviorSpec({

    fun buildService(
        seatLockStore: SeatLockStore = mockk(relaxed = true),
        ticketOrderRepository: TicketOrderRepository = mockk(relaxed = true),
    ) = TicketingDomainService(
        eventRepository = mockk(relaxed = true),
        seatRepository = mockk(relaxed = true),
        eventCustomRepository = mockk(relaxed = true),
        seatCustomRepository = mockk(relaxed = true),
        ticketOrderCustomRepository = mockk(relaxed = true),
        seatLockStore = seatLockStore,
        ticketOrderRepository = ticketOrderRepository,
        ticketRepository = mockk(relaxed = true),
    )

    Given("non-numeric lockId 토큰") {
        val service = buildService()
        When("[U-PARSE-01] verifyLockOwner 호출") {
            Then("MalformedLockIdException(400)이 던져진다") {
                shouldThrow<MalformedLockIdException> {
                    service.verifyLockOwner("abc:1", userId = 1L)
                }
            }
        }
    }

    Given("3-element 토큰 (`1:2:3`)") {
        val service = buildService()
        When("[U-PARSE-02] verifyLockOwner 호출") {
            Then("MalformedLockIdException(400)이 던져진다") {
                shouldThrow<MalformedLockIdException> {
                    service.verifyLockOwner("1:2:3", userId = 1L)
                }
            }
        }
    }

    Given("seatId만 있는 토큰 (`1:`)") {
        val service = buildService()
        When("[U-PARSE-03] verifyLockOwner 호출") {
            Then("MalformedLockIdException(400)이 던져진다") {
                shouldThrow<MalformedLockIdException> {
                    service.verifyLockOwner("1:", userId = 1L)
                }
            }
        }
    }

    Given("PENDING 상태 TicketOrder(id=200, eventId=1, seatIds=[10,20], userId=7)의 cancelOrder가 호출될 때") {
        val seatLockStore = mockk<SeatLockStore>(relaxed = true)
        val ticketOrderRepository = mockk<TicketOrderRepository>(relaxed = true)
        val service = buildService(seatLockStore = seatLockStore, ticketOrderRepository = ticketOrderRepository)

        val order = mockk<TicketOrder>(relaxed = true)
        every { order.id } returns 200L
        every { order.userId } returns 7L
        every { order.lockedEventId } returns 1L
        every { order.lockedSeatIds } returns listOf(10L, 20L)
        every { ticketOrderRepository.findById(200L) } returns order
        every { ticketOrderRepository.save(any()) } returns order
        every { seatLockStore.unlock(1L, 10L, 7L) } returns true
        every { seatLockStore.unlock(1L, 20L, 7L) } returns true

        When("[U-CANCEL-01] cancelOrder(200)를 호출하면") {
            service.cancelOrder(200L)

            Then("seatLockStore.unlock이 각 seatId(10, 20)에 대해 1회씩 호출된다") {
                verify(exactly = 1) { seatLockStore.unlock(1L, 10L, 7L) }
                verify(exactly = 1) { seatLockStore.unlock(1L, 20L, 7L) }
            }
        }
    }

    Given("cancelOrder 호출 시 seatLockStore.unlock이 예외를 던지는 경우") {
        val seatLockStore = mockk<SeatLockStore>(relaxed = true)
        val ticketOrderRepository = mockk<TicketOrderRepository>(relaxed = true)
        val service = buildService(seatLockStore = seatLockStore, ticketOrderRepository = ticketOrderRepository)

        val order = mockk<TicketOrder>(relaxed = true)
        every { order.id } returns 300L
        every { order.userId } returns 7L
        every { order.lockedEventId } returns 1L
        every { order.lockedSeatIds } returns listOf(10L)
        every { ticketOrderRepository.findById(300L) } returns order
        every { ticketOrderRepository.save(any()) } returns order
        every { seatLockStore.unlock(1L, 10L, 7L) } throws RuntimeException("Redis timeout")

        When("[U-CANCEL-02] cancelOrder(300)을 호출하면") {
            Then("unlock 실패에도 불구하고 cancelOrder가 정상 완료된다 (예외 전파 없음)") {
                service.cancelOrder(300L)
                verify(exactly = 1) { ticketOrderRepository.save(any()) }
            }
        }
    }
})
