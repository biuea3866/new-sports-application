package com.sportsapp.application.notification.dto

import com.sportsapp.domain.notification.PushToken

data class PushTokenResult(
    val id: Long,
    val platform: String,
) {
    companion object {
        fun of(pushToken: PushToken) = PushTokenResult(id = pushToken.id, platform = pushToken.platform.name)
    }
}
