package com.sportsapp.application.ticketing.usecase

import com.sportsapp.application.ticketing.dto.TicketOrderDetailResponse
import com.sportsapp.domain.ticketing.service.TicketingDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetTicketOrderUseCase(
    private val ticketingDomainService: TicketingDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(ticketOrderId: Long): TicketOrderDetailResponse {
        val detail = ticketingDomainService.getTicketOrderDetail(ticketOrderId)
        return TicketOrderDetailResponse.of(detail)
    }
}
