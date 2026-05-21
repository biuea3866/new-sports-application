package com.sportsapp.application.ticketing

import com.sportsapp.domain.ticketing.SeatSpec
import com.sportsapp.domain.ticketing.TicketingDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreateMyEventUseCase(
    private val ticketingDomainService: TicketingDomainService,
) {
    @Transactional
    fun execute(command: CreateMyEventCommand): MyEventResponse {
        val event = ticketingDomainService.createEvent(
            title = command.title,
            venue = command.venue,
            startsAt = command.startsAt,
            seats = command.seats.map { SeatSpec(it.section, it.rowNo, it.seatNo, it.price) },
            ownerUserId = command.ownerUserId,
        )
        return MyEventResponse.of(event)
    }
}
