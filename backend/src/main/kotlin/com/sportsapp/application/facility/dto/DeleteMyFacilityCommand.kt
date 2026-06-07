package com.sportsapp.application.facility.dto

data class DeleteMyFacilityCommand(
    val facilityId: String,
    val ownerUserId: Long,
)
