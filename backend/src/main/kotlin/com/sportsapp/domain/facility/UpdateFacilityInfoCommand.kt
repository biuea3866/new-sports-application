package com.sportsapp.domain.facility

data class UpdateFacilityInfoCommand(
    val name: String? = null,
    val address: String? = null,
    val operatingHours: String? = null,
    val basePrice: Long? = null,
)
