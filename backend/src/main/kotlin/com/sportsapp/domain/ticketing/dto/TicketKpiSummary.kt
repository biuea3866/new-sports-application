package com.sportsapp.domain.ticketing.dto

import java.math.BigDecimal

data class TicketKpiSummary(
    val totalSoldCount: Long,
    val refundRate: BigDecimal,
    val complimentaryCount: Long,
)
