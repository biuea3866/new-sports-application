package com.sportsapp.application.facility

data class DeleteMyFacilityCommand(
    val facilityId: String,
    val authUserId: Long,
)
