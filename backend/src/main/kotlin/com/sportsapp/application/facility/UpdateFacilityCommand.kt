package com.sportsapp.application.facility

data class UpdateFacilityCommand(
    val operatorId: Long,
    val facilityId: String,
    val name: String? = null,
    val address: String? = null,
    val operatingHours: String? = null,
    val basePrice: Long? = null,
)
