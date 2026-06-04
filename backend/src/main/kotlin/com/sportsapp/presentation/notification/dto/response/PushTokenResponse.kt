package com.sportsapp.presentation.notification.dto.response

import com.sportsapp.domain.notification.PushToken

data class PushTokenResponse(
    val id: Long,
    val platform: String,
) {
    companion object {
        fun of(pushToken: PushToken): PushTokenResponse =
            PushTokenResponse(id = pushToken.id, platform = pushToken.platform.name)
    }
}
