package com.sportsapp.application.ticketing.dto

import com.sportsapp.domain.ticketing.dto.TicketSalesSummary
import java.math.BigDecimal

data class TicketSalesResponse(
    val ownerUserId: Long,
    val totalTicketCount: Long,
    val totalRevenue: BigDecimal,
    val cancelledCount: Long,
) {
    companion object {
        fun of(ownerUserId: Long, summary: TicketSalesSummary): TicketSalesResponse =
            TicketSalesResponse(
                ownerUserId = ownerUserId,
                totalTicketCount = summary.totalTicketCount,
                totalRevenue = summary.totalRevenue,
                cancelledCount = summary.cancelledCount,
            )
    }
}
