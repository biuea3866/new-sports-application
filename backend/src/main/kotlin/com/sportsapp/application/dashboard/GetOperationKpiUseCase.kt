package com.sportsapp.application.dashboard

import com.sportsapp.domain.booking.service.BookingDomainService
import com.sportsapp.domain.goods.service.GoodsDomainService
import com.sportsapp.domain.ticketing.service.TicketingDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetOperationKpiUseCase(
    private val bookingDomainService: BookingDomainService,
    private val goodsDomainService: GoodsDomainService,
    private val ticketingDomainService: TicketingDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(command: GetOperationKpiCommand): GetOperationKpiResponse {
        val facilityKpi = bookingDomainService.aggregateFacilityKpi(command.ownerUserId, command.from, command.to)
        val goodsKpi = goodsDomainService.aggregateGoodsKpi(command.ownerUserId, command.from, command.to)
        val ticketKpi = ticketingDomainService.aggregateTicketKpi(command.ownerUserId, command.from, command.to)
        return GetOperationKpiResponse.of(command.ownerUserId, facilityKpi, goodsKpi, ticketKpi)
    }
}
