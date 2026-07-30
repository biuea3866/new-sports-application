package com.sportsapp.presentation.facility.dto.response

import com.sportsapp.domain.facility.dto.GuTypeCount

data class GuTypeCountResponse(
    val gu: String,
    val type: String,
    val count: Long,
) {
    companion object {
        fun of(guTypeCount: GuTypeCount): GuTypeCountResponse = GuTypeCountResponse(
            gu = guTypeCount.gu,
            type = guTypeCount.type,
            count = guTypeCount.count,
        )
    }
}
