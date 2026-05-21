package com.sportsapp.domain.facility

data class FacilityStats(
    val facilityId: String,
    val name: String,
    val totalBookings: Long,
    val totalRevenue: Long,
    val noShowCount: Long,
    val avgRating: Double?,
)
