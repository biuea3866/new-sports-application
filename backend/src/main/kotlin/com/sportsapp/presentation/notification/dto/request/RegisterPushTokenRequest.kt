package com.sportsapp.presentation.notification

import com.sportsapp.application.notification.RegisterPushTokenCommand
import com.sportsapp.domain.notification.PushPlatform

data class RegisterPushTokenRequest(
    val token: String,
    val platform: PushPlatform,
) {
    fun toCommand(userId: Long): RegisterPushTokenCommand =
        RegisterPushTokenCommand(userId = userId, token = token, platform = platform)
}
