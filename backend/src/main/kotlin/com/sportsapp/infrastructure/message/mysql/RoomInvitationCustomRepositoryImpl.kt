package com.sportsapp.infrastructure.message.mysql

import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.message.entity.QRoomInvitation
import com.sportsapp.domain.message.entity.RoomInvitation
import com.sportsapp.domain.message.repository.RoomInvitationCustomRepository
import com.sportsapp.domain.message.vo.InvitationStatus
import org.springframework.stereotype.Component

@Component
class RoomInvitationCustomRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : RoomInvitationCustomRepository {

    override fun findPendingBy(roomId: Long, inviteeUserId: Long): RoomInvitation? {
        val invitation = QRoomInvitation.roomInvitation
        return queryFactory.selectFrom(invitation)
            .where(
                invitation.room.id.eq(roomId),
                invitation.inviteeUserId.eq(inviteeUserId),
                invitation.status.eq(InvitationStatus.PENDING),
                invitation.deletedAt.isNull,
            )
            .fetchFirst()
    }

    override fun findPendingByInvitee(inviteeUserId: Long): List<RoomInvitation> {
        val invitation = QRoomInvitation.roomInvitation
        return queryFactory.selectFrom(invitation)
            .where(
                invitation.inviteeUserId.eq(inviteeUserId),
                invitation.status.eq(InvitationStatus.PENDING),
                invitation.deletedAt.isNull,
            )
            .fetch()
    }
}
