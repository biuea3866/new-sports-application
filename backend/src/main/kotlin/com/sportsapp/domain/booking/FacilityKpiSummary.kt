package com.sportsapp.domain.booking

import java.math.BigDecimal

data class FacilityKpiSummary(
    val utilizationRate: BigDecimal,
    val noShowRate: BigDecimal,
    val topFacilityIds: List<String>,
)
