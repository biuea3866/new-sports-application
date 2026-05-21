package com.sportsapp.application.facility

data class UpdateMyFacilityCommand(
    val facilityId: String,
    val patch: Map<String, String>,
    val authUserId: Long,
)
