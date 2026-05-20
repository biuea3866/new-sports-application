package com.sportsapp.application.ticketing

import com.sportsapp.domain.ticketing.TicketingDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetEventUseCase(
    private val ticketingDomainService: TicketingDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(eventId: Long): EventDetailResponse {
        val event = ticketingDomainService.getEvent(eventId)
        val seats = ticketingDomainService.getSeats(eventId)
        return EventDetailResponse.of(event, seats)
    }
}
