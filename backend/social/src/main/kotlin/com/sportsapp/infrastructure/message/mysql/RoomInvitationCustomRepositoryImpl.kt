package com.sportsapp.infrastructure.message.mysql

import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.message.entity.QRoom
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

    /**
     * `room`을 fetch join 한다 — 응답(`InvitationResponse.roomName`)이 방 이름을 담으므로,
     * join 없이 두면 초대 건수만큼 SELECT 가 추가되고(N+1) 트랜잭션이 닫힌 뒤
     * (`open-in-view: false`) 프록시를 건드리는 경로가 생긴다.
     */
    override fun findPendingByInvitee(inviteeUserId: Long): List<RoomInvitation> {
        val invitation = QRoomInvitation.roomInvitation
        val room = QRoom.room
        return queryFactory.selectFrom(invitation)
            .join(invitation.room, room).fetchJoin()
            .where(
                invitation.inviteeUserId.eq(inviteeUserId),
                invitation.status.eq(InvitationStatus.PENDING),
                invitation.deletedAt.isNull,
            )
            .fetch()
    }
}
