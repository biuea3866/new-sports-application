package com.sportsapp.application.ticketing.usecase

import com.sportsapp.domain.ticketing.dto.EventCriteria
import com.sportsapp.application.ticketing.dto.EventResponse
import com.sportsapp.domain.ticketing.service.TicketingDomainService
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
