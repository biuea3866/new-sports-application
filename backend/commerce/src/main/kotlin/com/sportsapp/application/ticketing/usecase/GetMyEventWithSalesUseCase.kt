package com.sportsapp.application.ticketing.usecase

import com.sportsapp.domain.common.security.OwnershipGuard
import com.sportsapp.application.ticketing.dto.MyEventWithSalesResponse
import com.sportsapp.domain.ticketing.service.TicketingDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetMyEventWithSalesUseCase(
    private val ticketingDomainService: TicketingDomainService,
    private val ownershipGuard: OwnershipGuard,
) {
    @Transactional(readOnly = true)
    fun execute(eventId: Long, authUserId: Long): MyEventWithSalesResponse {
        val event = ticketingDomainService.getEvent(eventId)
        ownershipGuard.requireOwned(event.ownerId, authUserId)
        val salesInfo = ticketingDomainService.getEventSalesInfo(eventId)
        return MyEventWithSalesResponse.of(salesInfo)
    }
}
