package com.sportsapp.application.notification

import com.sportsapp.domain.notification.PushPlatform

data class RegisterPushTokenCommand(
    val userId: Long,
    val token: String,
    val platform: PushPlatform,
)
