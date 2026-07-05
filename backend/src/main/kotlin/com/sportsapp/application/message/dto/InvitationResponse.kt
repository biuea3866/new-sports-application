package com.sportsapp.application.message.dto

import com.sportsapp.domain.message.entity.RoomInvitation
import com.sportsapp.domain.message.vo.InvitationStatus
import java.time.ZonedDateTime

/**
 * TDD "응답 DTO 필드 스키마 — InvitationResponse" (FE-BE 계약 확정).
 */
data class InvitationResponse(
    val id: Long,
    val roomId: Long,
    val inviterUserId: Long,
    val inviteeUserId: Long,
    val status: InvitationStatus,
    val canSpeak: Boolean,
    val expiresAt: ZonedDateTime,
    val createdAt: ZonedDateTime,
) {
    companion object {
        fun of(invitation: RoomInvitation): InvitationResponse = InvitationResponse(
            id = invitation.id,
            roomId = invitation.room.id,
            inviterUserId = invitation.inviterUserId,
            inviteeUserId = invitation.inviteeUserId,
            status = invitation.currentStatus,
            canSpeak = invitation.canSpeak,
            expiresAt = invitation.expiresAt,
            createdAt = invitation.createdAt,
        )
    }
}
