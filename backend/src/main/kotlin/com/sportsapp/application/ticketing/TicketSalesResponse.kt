package com.sportsapp.application.ticketing

import com.sportsapp.domain.ticketing.TicketSalesSummary
import java.math.BigDecimal

data class TicketSalesResponse(
    val totalTicketCount: Long,
    val totalRevenue: BigDecimal,
    val cancelledCount: Long,
) {
    companion object {
        fun of(summary: TicketSalesSummary): TicketSalesResponse = TicketSalesResponse(
            totalTicketCount = summary.totalTicketCount,
            totalRevenue = summary.totalRevenue,
            cancelledCount = summary.cancelledCount,
        )
    }
}
