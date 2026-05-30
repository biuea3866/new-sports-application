package com.sportsapp.application.ticketing

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.common.security.OwnershipGuard
import com.sportsapp.domain.ticketing.Event
import com.sportsapp.domain.ticketing.EventStatus
import com.sportsapp.domain.ticketing.TicketingDomainService
import com.sportsapp.domain.ticketing.exception.InvalidEventStateException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import java.time.ZoneOffset
import java.time.ZonedDateTime

class DeleteMyEventUseCaseTest : BehaviorSpec({

    val ticketingDomainService = mockk<TicketingDomainService>()
    val ownershipGuard = mockk<OwnershipGuard>()
    val deleteMyEventUseCase = DeleteMyEventUseCase(ticketingDomainService, ownershipGuard)

    val authUserId = 10L
    val eventId = 1L

    fun buildScheduledEvent() = Event(
        id = eventId,
        title = "Test Event",
        venue = "Stadium",
        startsAt = ZonedDateTime.of(2027, 6, 1, 18, 0, 0, 0, ZoneOffset.UTC),
        status = EventStatus.SCHEDULED,
        ownerId = authUserId,
    )

    fun buildOpenEvent() = Event(
        id = eventId,
        title = "Open Event",
        venue = "Stadium",
        startsAt = ZonedDateTime.of(2027, 6, 1, 18, 0, 0, 0, ZoneOffset.UTC),
        status = EventStatus.OPEN,
        ownerId = authUserId,
    )

    Given("SCHEDULED 상태 경기 + 본인 host") {
        val event = buildScheduledEvent()
        every { ticketingDomainService.getEvent(eventId) } returns event
        justRun { ownershipGuard.requireOwned(authUserId, authUserId) }
        justRun { ticketingDomainService.deleteEvent(eventId, authUserId) }

        When("[U-06] execute(eventId, authUserId) 호출") {
            deleteMyEventUseCase.execute(eventId, authUserId)

            Then("ticketingDomainService.deleteEvent가 1회 호출된다") {
                verify(exactly = 1) { ticketingDomainService.deleteEvent(eventId, authUserId) }
            }
        }
    }

    Given("OPEN 상태 경기 (티켓 발행됨)") {
        val event = buildOpenEvent()
        every { ticketingDomainService.getEvent(eventId) } returns event
        justRun { ownershipGuard.requireOwned(authUserId, authUserId) }
        every { ticketingDomainService.deleteEvent(eventId, authUserId) } throws
            InvalidEventStateException("Cannot delete event in OPEN state")

        When("[U-07] execute(eventId, authUserId) 호출") {
            Then("InvalidEventStateException을 던진다") {
                shouldThrow<InvalidEventStateException> {
                    deleteMyEventUseCase.execute(eventId, authUserId)
                }
            }
        }
    }

    Given("존재하지 않는 경기") {
        every { ticketingDomainService.getEvent(eventId) } throws
            ResourceNotFoundException("Event", eventId)

        When("[U-08] execute(eventId, authUserId) 호출") {
            Then("ResourceNotFoundException을 던진다") {
                shouldThrow<ResourceNotFoundException> {
                    deleteMyEventUseCase.execute(eventId, authUserId)
                }
            }
        }
    }

    Given("다른 owner 소유 경기") {
        val anotherOwnerId = 99L
        val event = Event(
            id = eventId,
            title = "Another Owner Event",
            venue = "Stadium",
            startsAt = ZonedDateTime.of(2027, 6, 1, 18, 0, 0, 0, ZoneOffset.UTC),
            status = EventStatus.SCHEDULED,
            ownerId = anotherOwnerId,
        )
        every { ticketingDomainService.getEvent(eventId) } returns event
        every { ownershipGuard.requireOwned(anotherOwnerId, authUserId) } throws
            ResourceNotFoundException("Event", eventId)

        When("[U-09] execute(eventId, authUserId) 호출") {
            Then("ResourceNotFoundException(404)을 던진다") {
                shouldThrow<ResourceNotFoundException> {
                    deleteMyEventUseCase.execute(eventId, authUserId)
                }
            }
        }
    }
})
