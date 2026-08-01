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
    /**
     * 초대 수신함이 방 PK(`방 #53`) 대신 사람이 읽는 이름을 보여줄 수 있도록 함께 내려준다.
     * 1:1 방처럼 이름이 없는 방은 null이며, 이때 이름은 클라이언트가 방 종류로 결정한다.
     */
    val roomName: String?,
    val inviterUserId: Long,
    val inviteeUserId: Long,
    val status: InvitationStatus,
    val canSpeak: Boolean,
    val expiresAt: ZonedDateTime,
    val createdAt: ZonedDateTime,
    val reused: Boolean,
) {
    companion object {
        fun of(invitation: RoomInvitation, reused: Boolean = false): InvitationResponse = InvitationResponse(
            id = invitation.id,
            roomId = invitation.room.id,
            roomName = invitation.room.name,
            inviterUserId = invitation.inviterUserId,
            inviteeUserId = invitation.inviteeUserId,
            status = invitation.currentStatus,
            canSpeak = invitation.canSpeak,
            expiresAt = invitation.expiresAt,
            createdAt = invitation.createdAt,
            reused = reused,
        )
    }
}
