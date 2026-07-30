package com.sportsapp.presentation.message.dto.request

import com.sportsapp.application.message.dto.InviteGuestCommand

data class InviteGuestRequest(
    val inviteeUserId: Long,
    val canSpeak: Boolean,
    val expiresInDays: Long,
) {
    fun toCommand(roomId: Long, inviterUserId: Long): InviteGuestCommand = InviteGuestCommand(
        roomId = roomId,
        inviterUserId = inviterUserId,
        inviteeUserId = inviteeUserId,
        canSpeak = canSpeak,
        expiresInDays = expiresInDays,
    )
}
