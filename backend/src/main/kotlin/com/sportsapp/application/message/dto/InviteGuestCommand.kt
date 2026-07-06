package com.sportsapp.application.message.dto

data class InviteGuestCommand(
    val roomId: Long,
    val inviterUserId: Long,
    val inviteeUserId: Long,
    val canSpeak: Boolean,
    val expiresInDays: Long,
)
