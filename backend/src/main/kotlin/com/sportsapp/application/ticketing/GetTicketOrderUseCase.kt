package com.sportsapp.application.ticketing

import com.sportsapp.domain.ticketing.TicketingDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetTicketOrderUseCase(
    private val ticketingDomainService: TicketingDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(ticketOrderId: Long): TicketOrderResponse {
        val order = ticketingDomainService.getTicketOrder(ticketOrderId)
        return TicketOrderResponse.of(order)
    }
}
