package com.sportsapp.domain.ticketing

import java.math.BigDecimal

data class TicketSalesSummary(
    val totalTicketCount: Long,
    val totalRevenue: BigDecimal,
    val cancelledCount: Long,
)
