package com.sportsapp.infrastructure.message.mysql

import com.sportsapp.domain.message.entity.RoomInvitation
import com.sportsapp.domain.message.repository.RoomInvitationCustomRepository
import com.sportsapp.domain.message.repository.RoomInvitationRepository
import org.springframework.stereotype.Component

@Component
class RoomInvitationRepositoryImpl(
    private val roomInvitationJpaRepository: RoomInvitationJpaRepository,
    private val roomInvitationCustomRepository: RoomInvitationCustomRepository,
) : RoomInvitationRepository {

    override fun save(invitation: RoomInvitation): RoomInvitation =
        roomInvitationJpaRepository.save(invitation)

    override fun findById(id: Long): RoomInvitation? =
        roomInvitationJpaRepository.findByIdAndDeletedAtIsNull(id)

    override fun findPendingBy(roomId: Long, inviteeUserId: Long): RoomInvitation? =
        roomInvitationCustomRepository.findPendingBy(roomId, inviteeUserId)

    override fun findPendingByInvitee(inviteeUserId: Long): List<RoomInvitation> =
        roomInvitationCustomRepository.findPendingByInvitee(inviteeUserId)
}
