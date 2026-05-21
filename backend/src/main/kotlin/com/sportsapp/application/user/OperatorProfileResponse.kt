package com.sportsapp.application.user

import com.sportsapp.domain.user.OperatorProfile
import com.sportsapp.domain.user.UserStatus

data class OperatorProfileResponse(
    val userId: Long,
    val email: String,
    val status: UserStatus,
    val facilityCount: Long,
    val activeProductCount: Long,
    val activeTokenCount: Long,
) {
    companion object {
        fun of(profile: OperatorProfile): OperatorProfileResponse = OperatorProfileResponse(
            userId = profile.userId,
            email = profile.email,
            status = profile.status,
            facilityCount = profile.facilityCount,
            activeProductCount = profile.activeProductCount,
            activeTokenCount = profile.activeTokenCount,
        )
    }
}
