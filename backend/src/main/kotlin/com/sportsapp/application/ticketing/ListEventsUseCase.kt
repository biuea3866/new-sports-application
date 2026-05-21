package com.sportsapp.application.ticketing

import com.sportsapp.domain.ticketing.EventCriteria
import com.sportsapp.domain.ticketing.TicketingDomainService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListEventsUseCase(
    private val ticketingDomainService: TicketingDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(criteria: EventCriteria, pageable: Pageable): Page<EventResponse> =
        ticketingDomainService.listEvents(criteria, pageable).map { EventResponse.of(it) }
}
