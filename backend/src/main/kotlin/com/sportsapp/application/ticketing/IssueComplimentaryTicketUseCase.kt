package com.sportsapp.application.ticketing

import com.sportsapp.domain.ticketing.TicketingDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class IssueComplimentaryTicketUseCase(
    private val ticketingDomainService: TicketingDomainService,
) {
    @Transactional
    fun execute(command: IssueComplimentaryTicketCommand): IssueComplimentaryTicketResponse {
        val ticket = ticketingDomainService.issueComplimentary(
            eventId = command.eventId,
            seatId = command.seatId,
            operatorUserId = command.operatorUserId,
        )
        return IssueComplimentaryTicketResponse.of(ticket)
    }
}
