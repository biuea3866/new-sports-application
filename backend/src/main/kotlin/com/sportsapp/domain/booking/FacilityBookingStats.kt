package com.sportsapp.domain.booking

data class FacilityBookingStats(
    val facilityId: String,
    val totalBookings: Long,
    val totalRevenue: Long,
    val noShowCount: Long,
)
