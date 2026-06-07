package com.sportsapp.application.ticketing.usecase

import com.sportsapp.application.ticketing.dto.IssueComplimentaryTicketCommand
import com.sportsapp.application.ticketing.dto.IssueComplimentaryTicketResponse
import com.sportsapp.domain.ticketing.service.TicketingDomainService
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
