package com.sportsapp.domain.ticketing.dto

import java.math.BigDecimal

data class TicketSalesSummary(
    val totalTicketCount: Long,
    val totalRevenue: BigDecimal,
    val cancelledCount: Long,
)
