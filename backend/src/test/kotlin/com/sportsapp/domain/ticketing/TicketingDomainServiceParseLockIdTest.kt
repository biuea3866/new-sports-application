package com.sportsapp.domain.ticketing

import com.sportsapp.domain.ticketing.exception.MalformedLockIdException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.mockk

class TicketingDomainServiceParseLockIdTest : BehaviorSpec({

    val eventRepository = mockk<EventRepository>(relaxed = true)
    val seatRepository = mockk<SeatRepository>(relaxed = true)
    val customEventRepository = mockk<CustomEventRepository>(relaxed = true)
    val seatLockStore = mockk<SeatLockStore>(relaxed = true)
    val ticketOrderRepository = mockk<TicketOrderRepository>(relaxed = true)
    val service = TicketingDomainService(
        eventRepository = eventRepository,
        seatRepository = seatRepository,
        customEventRepository = customEventRepository,
        seatLockStore = seatLockStore,
        ticketOrderRepository = ticketOrderRepository,
    )

    Given("non-numeric lockId 토큰") {
        When("[U-PARSE-01] verifyLockOwner 호출") {
            Then("MalformedLockIdException(400)이 던져진다") {
                shouldThrow<MalformedLockIdException> {
                    service.verifyLockOwner("abc:1", userId = 1L)
                }
            }
        }
    }

    Given("3-element 토큰 (`1:2:3`)") {
        When("[U-PARSE-02] verifyLockOwner 호출") {
            Then("MalformedLockIdException(400)이 던져진다") {
                shouldThrow<MalformedLockIdException> {
                    service.verifyLockOwner("1:2:3", userId = 1L)
                }
            }
        }
    }

    Given("seatId만 있는 토큰 (`1:`)") {
        When("[U-PARSE-03] verifyLockOwner 호출") {
            Then("MalformedLockIdException(400)이 던져진다") {
                shouldThrow<MalformedLockIdException> {
                    service.verifyLockOwner("1:", userId = 1L)
                }
            }
        }
    }
})
