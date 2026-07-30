package com.sportsapp.domain.booking.dto

import java.math.BigDecimal

data class FacilityKpiSummary(
    val utilizationRate: BigDecimal,
    val noShowRate: BigDecimal,
    val topFacilityIds: List<String>,
)
