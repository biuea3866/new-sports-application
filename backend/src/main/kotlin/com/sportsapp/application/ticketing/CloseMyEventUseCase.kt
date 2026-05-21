package com.sportsapp.application.ticketing

import com.sportsapp.domain.ticketing.TicketingDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CloseMyEventUseCase(
    private val ticketingDomainService: TicketingDomainService,
) {
    @Transactional
    fun execute(eventId: Long, ownerUserId: Long): MyEventResponse {
        val event = ticketingDomainService.closeMyEvent(eventId, ownerUserId)
        val count = ticketingDomainService.countConfirmedSeatsByEventId(eventId)
        return MyEventResponse.of(event, count)
    }
}
