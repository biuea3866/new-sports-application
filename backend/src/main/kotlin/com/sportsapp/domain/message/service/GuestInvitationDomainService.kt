package com.sportsapp.domain.message.service

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.message.entity.RoomInvitation
import com.sportsapp.domain.message.entity.RoomParticipant
import com.sportsapp.domain.message.exception.NotRoomHostException
import com.sportsapp.domain.message.repository.RoomInvitationRepository
import com.sportsapp.domain.message.repository.RoomParticipantRepository
import com.sportsapp.domain.message.repository.RoomRepository
import org.springframework.stereotype.Service

/**
 * 게스트 초대 수명주기 — TDD "GuestInvitationDomainService"(FR-11/12/13/14).
 *
 * "방장" 개념은 별도 필드가 없어(Open Questions 미결), 방의 활성 MEMBER 참여자 중
 * 가장 먼저 참여한(joinedAt 최솟값) 참여자를 방장으로 판단한다.
 */
@Service
class GuestInvitationDomainService(
    private val roomRepository: RoomRepository,
    private val roomParticipantRepository: RoomParticipantRepository,
    private val invitationRepository: RoomInvitationRepository,
) {

    fun invite(
        roomId: Long,
        inviterUserId: Long,
        inviteeUserId: Long,
        canSpeak: Boolean,
        expiresInDays: Long,
    ): RoomInvitation {
        requireHost(roomId, inviterUserId)
        invitationRepository.findPendingBy(roomId, inviteeUserId)?.let { return it }
        val room = roomRepository.findById(roomId) ?: throw ResourceNotFoundException("Room", roomId)
        val invitation = RoomInvitation.create(room, inviterUserId, inviteeUserId, canSpeak, expiresInDays)
        return invitationRepository.save(invitation)
    }

    fun accept(invitationId: Long, userId: Long): RoomInvitation {
        val invitation = findInvitationBy(invitationId)
        invitation.validateInvitee(userId)
        invitation.accept()
        val accepted = invitationRepository.save(invitation)
        roomParticipantRepository.save(
            RoomParticipant.forGuest(
                room = invitation.room,
                userId = invitation.inviteeUserId,
                canSpeak = invitation.canSpeak,
                expiresInDays = invitation.remainingExpiryDays(),
            ),
        )
        return accepted
    }

    fun reject(invitationId: Long, userId: Long): RoomInvitation {
        val invitation = findInvitationBy(invitationId)
        invitation.validateInvitee(userId)
        invitation.reject()
        return invitationRepository.save(invitation)
    }

    fun revoke(invitationId: Long, hostUserId: Long): RoomInvitation {
        val invitation = findInvitationBy(invitationId)
        requireHost(invitation.room.id, hostUserId)
        invitation.revoke()
        return invitationRepository.save(invitation)
    }

    fun findMyPendingInvitations(userId: Long): List<RoomInvitation> =
        invitationRepository.findPendingByInvitee(userId)

    private fun findInvitationBy(invitationId: Long): RoomInvitation =
        invitationRepository.findById(invitationId)
            ?: throw ResourceNotFoundException("RoomInvitation", invitationId)

    private fun requireHost(roomId: Long, userId: Long) {
        val host = roomParticipantRepository.findActiveByRoomId(roomId)
            .filter { it.isMember() }
            .minByOrNull { it.joinedAt }
        if (host == null || host.userId != userId) throw NotRoomHostException(userId, roomId)
    }
}
