package com.sportsapp.application.ticketing.usecase

import com.sportsapp.application.ticketing.dto.GetTicketSalesCommand
import com.sportsapp.application.ticketing.dto.TicketSalesResponse
import com.sportsapp.domain.ticketing.service.TicketingDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetTicketSalesUseCase(
    private val ticketingDomainService: TicketingDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(command: GetTicketSalesCommand): TicketSalesResponse {
        val summary = ticketingDomainService.aggregateTicketSales(
            ownerUserId = command.ownerUserId,
            eventId = command.eventId,
            from = command.from,
            to = command.to,
        )
        return TicketSalesResponse.of(command.ownerUserId, summary)
    }
}
