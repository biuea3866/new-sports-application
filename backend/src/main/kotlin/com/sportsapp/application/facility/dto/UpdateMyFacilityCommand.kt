package com.sportsapp.application.facility.dto

data class UpdateMyFacilityCommand(
    val facilityId: String,
    val ownerUserId: Long,
    val patch: Map<String, String>,
    val sido: String? = null,
)
