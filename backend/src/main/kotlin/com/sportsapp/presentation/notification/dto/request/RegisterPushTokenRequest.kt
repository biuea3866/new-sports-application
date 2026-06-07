package com.sportsapp.presentation.notification.dto.request
import com.sportsapp.application.notification.dto.RegisterPushTokenCommand
import com.sportsapp.domain.notification.entity.PushPlatform
data class RegisterPushTokenRequest(
    val token: String,
    val platform: PushPlatform,
) {
    fun toCommand(userId: Long): RegisterPushTokenCommand =
        RegisterPushTokenCommand(userId = userId, token = token, platform = platform)
}
