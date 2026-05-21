package com.sportsapp.application.ticketing

import com.sportsapp.domain.ticketing.TicketingDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UpdateMyEventUseCase(
    private val ticketingDomainService: TicketingDomainService,
) {
    @Transactional
    fun execute(command: UpdateMyEventCommand): MyEventResponse {
        val event = ticketingDomainService.updateMyEvent(
            eventId = command.eventId,
            ownerUserId = command.ownerUserId,
            title = command.title,
            venue = command.venue,
            startsAt = command.startsAt,
        )
        val count = ticketingDomainService.countConfirmedOrdersByEventId(command.eventId)
        return MyEventResponse.of(event, count)
    }
}
