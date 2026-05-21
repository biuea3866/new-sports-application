package com.sportsapp.application.ticketing

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
    fun execute(ownerUserId: Long, pageable: Pageable): Page<MyEventResponse> {
        val events = ticketingDomainService.listMyEvents(ownerUserId, pageable)
        return events.map { event ->
            val count = ticketingDomainService.countConfirmedSeatsByEventId(event.id)
            MyEventResponse.of(event, count)
        }
    }
}
