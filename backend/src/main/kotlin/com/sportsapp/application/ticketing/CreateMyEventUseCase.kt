package com.sportsapp.application.ticketing

import com.sportsapp.domain.ticketing.TicketingDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreateMyEventUseCase(
    private val ticketingDomainService: TicketingDomainService,
) {
    @Transactional
    fun execute(command: CreateMyEventCommand): CreateMyEventResult {
        val event = ticketingDomainService.createEvent(
            title = command.title,
            venue = command.venue,
            startsAt = command.startsAt,
            seats = command.seats,
            ownerUserId = command.ownerUserId,
        )
        return CreateMyEventResult.of(event, command.seats.size)
    }
}
