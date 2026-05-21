package com.sportsapp.application.dashboard

data class DashboardSummaryResponse(
    val facilities: FacilitiesSummary?,
    val events: EventsSummary?,
    val products: ProductsSummary?,
) {
    data class FacilitiesSummary(
        val count: Long,
        val activeSlotsToday: Long,
    )

    data class EventsSummary(
        val scheduled: Long,
        val open: Long,
        val closed: Long,
        val totalSeats: Long,
        val soldSeats: Long,
    )

    data class ProductsSummary(
        val active: Long,
        val outOfStock: Long,
    )
}
