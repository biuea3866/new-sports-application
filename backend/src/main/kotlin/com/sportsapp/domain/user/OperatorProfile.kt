package com.sportsapp.domain.user

data class OperatorProfile(
    val userId: Long,
    val email: String,
    val status: UserStatus,
    val facilityCount: Long,
    val activeProductCount: Long,
    val activeTokenCount: Long,
)
