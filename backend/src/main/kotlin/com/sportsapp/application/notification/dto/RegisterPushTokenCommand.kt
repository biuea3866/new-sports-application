package com.sportsapp.application.notification.dto
import com.sportsapp.domain.notification.entity.PushPlatform
data class RegisterPushTokenCommand(
    val userId: Long,
    val token: String,
    val platform: PushPlatform,
)
