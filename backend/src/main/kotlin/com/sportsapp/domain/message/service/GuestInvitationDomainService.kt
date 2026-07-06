package com.sportsapp.domain.message.service

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.entity.RoomInvitation
import com.sportsapp.domain.message.entity.RoomParticipant
import com.sportsapp.domain.message.repository.RoomInvitationRepository
import com.sportsapp.domain.message.repository.RoomParticipantRepository
import com.sportsapp.domain.message.repository.RoomRepository
import org.springframework.stereotype.Service

/**
 * 게스트 초대 수명주기 — TDD "GuestInvitationDomainService"(FR-11/12/13/14).
 *
 * 방장 판정은 `rooms.host_user_id`(BE-13) 단일 소스를 [Room.requireHostedBy]로 위임한다 —
 * 과거에는 활성 MEMBER 중 최초 참여자를 방장으로 추론했으나(참여자 유형별 판정 비대칭 문제,
 * [GuestEvictionDomainService] 참고), 이제 두 서비스가 동일 판정을 공유한다.
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
        val room = findRoomBy(roomId)
        room.requireHostedBy(inviterUserId)
        invitationRepository.findPendingBy(roomId, inviteeUserId)?.let { return it }
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
        invitation.room.requireHostedBy(hostUserId)
        invitation.revoke()
        return invitationRepository.save(invitation)
    }

    fun findMyPendingInvitations(userId: Long): List<RoomInvitation> =
        invitationRepository.findPendingByInvitee(userId)

    private fun findInvitationBy(invitationId: Long): RoomInvitation =
        invitationRepository.findById(invitationId)
            ?: throw ResourceNotFoundException("RoomInvitation", invitationId)

    private fun findRoomBy(roomId: Long): Room =
        roomRepository.findById(roomId) ?: throw ResourceNotFoundException("Room", roomId)
}
