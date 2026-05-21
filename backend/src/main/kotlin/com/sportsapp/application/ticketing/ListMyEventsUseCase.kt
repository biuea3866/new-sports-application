package com.sportsapp.application.ticketing

import com.sportsapp.domain.ticketing.EventStatus
import com.sportsapp.domain.ticketing.TicketingDomainService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListMyEventsUseCase(
    private val ticketingDomainService: TicketingDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(authUserId: Long, pageable: Pageable, statusFilter: EventStatus?): Page<EventResponse> =
        ticketingDomainService.findEventsByOwnerId(authUserId, pageable, statusFilter)
            .map { EventResponse.of(it) }
}
