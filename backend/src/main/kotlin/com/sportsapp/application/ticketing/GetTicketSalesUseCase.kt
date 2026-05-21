package com.sportsapp.application.ticketing

import com.sportsapp.domain.common.exceptions.UnauthorizedException
import com.sportsapp.domain.ticketing.TicketingDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetTicketSalesUseCase(
    private val ticketingDomainService: TicketingDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(command: GetTicketSalesCommand): TicketSalesResponse {
        requireEventOwnership(command)
        val summary = ticketingDomainService.aggregateTicketSales(
            ownerUserId = command.operatorUserId,
            eventId = command.eventId,
            from = command.from,
            to = command.to,
        )
        return TicketSalesResponse.of(summary)
    }

    private fun requireEventOwnership(command: GetTicketSalesCommand) {
        if (command.eventId != null) {
            val event = ticketingDomainService.getEvent(command.eventId)
            if (event.ownerId != command.operatorUserId) {
                throw UnauthorizedException("Event ${command.eventId} is not owned by operator ${command.operatorUserId}")
            }
        }
    }
}
