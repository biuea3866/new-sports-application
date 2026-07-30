package com.sportsapp.application.ticketing.usecase

import com.sportsapp.application.ticketing.dto.EventDetailResponse
import com.sportsapp.domain.ticketing.service.TicketingDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetEventUseCase(
    private val ticketingDomainService: TicketingDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(eventId: Long): EventDetailResponse {
        val event = ticketingDomainService.getEvent(eventId)
        val seatsWithAvailability = ticketingDomainService.getSeatsWithAvailability(eventId)
        return EventDetailResponse.of(event, seatsWithAvailability)
    }
}
