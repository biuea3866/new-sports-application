package com.sportsapp.application.ticketing

import com.sportsapp.domain.ticketing.TicketingDomainService
import org.springframework.stereotype.Service

@Service
class ReleaseSeatsUseCase(
    private val ticketingDomainService: TicketingDomainService,
) {
    fun execute(command: ReleaseSeatsCommand) {
        ticketingDomainService.releaseSeats(command.eventId, command.seatIds, command.userId)
    }
}
