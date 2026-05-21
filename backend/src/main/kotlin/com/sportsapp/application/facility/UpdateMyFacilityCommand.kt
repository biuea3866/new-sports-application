package com.sportsapp.application.facility

data class UpdateMyFacilityCommand(
    val facilityId: String,
    val ownerUserId: Long,
    val patch: Map<String, String>,
)
