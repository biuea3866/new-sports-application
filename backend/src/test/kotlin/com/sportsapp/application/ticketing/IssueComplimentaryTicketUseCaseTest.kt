package com.sportsapp.application.ticketing

import com.sportsapp.domain.ticketing.EventOwnershipException
import com.sportsapp.domain.ticketing.Ticket
import com.sportsapp.domain.ticketing.TicketStatus
import com.sportsapp.domain.ticketing.TicketingDomainService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class IssueComplimentaryTicketUseCaseTest : BehaviorSpec({

    val ticketingDomainService = mockk<TicketingDomainService>()
    val useCase = IssueComplimentaryTicketUseCase(ticketingDomainService)

    Given("[U-01] 이벤트 소유자가 정상 무료 티켓 발급 요청") {
        val command = IssueComplimentaryTicketCommand(
            eventId = 5L,
            seatId = 10L,
            operatorUserId = 77L,
        )
        val ticket = mockk<Ticket> {
            every { id } returns 500L
            every { seatId } returns 10L
            every { status } returns TicketStatus.ISSUED
            every { code } returns "abcdef1234567890abcdef1234567890"
        }
        every { ticketingDomainService.issueComplimentary(5L, 10L, 77L) } returns ticket

        When("execute 를 호출하면") {
            val result = useCase.execute(command)

            Then("[U-01] IssueComplimentaryTicketResponse 가 반환되고 status 가 ISSUED 이다") {
                result.ticketId shouldBe 500L
                result.seatId shouldBe 10L
                result.status shouldBe TicketStatus.ISSUED
                verify(exactly = 1) { ticketingDomainService.issueComplimentary(5L, 10L, 77L) }
            }
        }
    }

    Given("[U-02] 이벤트 소유자가 아닌 운영자가 무료 티켓 발급 시도") {
        val command = IssueComplimentaryTicketCommand(
            eventId = 5L,
            seatId = 10L,
            operatorUserId = 99L,
        )
        every { ticketingDomainService.issueComplimentary(5L, 10L, 99L) } throws EventOwnershipException(5L)

        When("execute 를 호출하면") {
            Then("[U-02] EventOwnershipException 이 전파된다") {
                shouldThrow<EventOwnershipException> {
                    useCase.execute(command)
                }
            }
        }
    }
})
