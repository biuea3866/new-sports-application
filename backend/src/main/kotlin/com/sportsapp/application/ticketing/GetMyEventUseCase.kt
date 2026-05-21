package com.sportsapp.application.ticketing

import com.sportsapp.domain.ticketing.TicketingDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetMyEventUseCase(
    private val ticketingDomainService: TicketingDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(eventId: Long, ownerUserId: Long): MyEventResponse {
        val event = ticketingDomainService.getMyEvent(eventId, ownerUserId)
        val count = ticketingDomainService.countConfirmedSeatsByEventId(eventId)
        return MyEventResponse.of(event, count)
    }
}
