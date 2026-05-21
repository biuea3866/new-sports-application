package com.sportsapp.application.facility

import com.sportsapp.domain.facility.FacilityStats

data class FacilityStatsResponse(
    val facilityId: String,
    val name: String,
    val totalBookings: Long,
    val totalRevenue: Long,
    val noShowCount: Long,
    val avgRating: Double?,
) {
    companion object {
        fun of(stats: FacilityStats): FacilityStatsResponse = FacilityStatsResponse(
            facilityId = stats.facilityId,
            name = stats.name,
            totalBookings = stats.totalBookings,
            totalRevenue = stats.totalRevenue,
            noShowCount = stats.noShowCount,
            avgRating = stats.avgRating,
        )
    }
}
