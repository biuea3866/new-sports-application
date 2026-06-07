package com.sportsapp.application.ticketing.usecase

import com.sportsapp.domain.common.security.OwnershipGuard
import com.sportsapp.domain.ticketing.service.TicketingDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OpenMyEventUseCase(
    private val ticketingDomainService: TicketingDomainService,
    private val ownershipGuard: OwnershipGuard,
) {
    @Transactional
    fun execute(eventId: Long, authUserId: Long) {
        val event = ticketingDomainService.getEvent(eventId)
        ownershipGuard.requireOwned(event.ownerId, authUserId)
        ticketingDomainService.openEvent(eventId)
    }
}
