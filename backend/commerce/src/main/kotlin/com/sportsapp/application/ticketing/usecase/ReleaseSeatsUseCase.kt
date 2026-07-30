package com.sportsapp.application.ticketing.usecase

import com.sportsapp.application.ticketing.dto.ReleaseSeatsCommand
import com.sportsapp.domain.ticketing.service.TicketingDomainService
import org.springframework.stereotype.Service

@Service
class ReleaseSeatsUseCase(
    private val ticketingDomainService: TicketingDomainService,
) {
    fun execute(command: ReleaseSeatsCommand) {
        ticketingDomainService.releaseSeats(command.eventId, command.seatIds, command.userId)
    }
}
