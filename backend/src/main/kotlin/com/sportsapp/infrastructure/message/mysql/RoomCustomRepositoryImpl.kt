package com.sportsapp.infrastructure.message.mysql

import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.message.entity.QRoom
import com.sportsapp.domain.message.entity.QRoomParticipant
import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.repository.RoomCustomRepository
import com.sportsapp.domain.message.vo.RoomType
import org.springframework.stereotype.Component

@Component
class RoomCustomRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : RoomCustomRepository {

    override fun findDirectRoomByParticipantIds(userIdA: Long, userIdB: Long): Room? {
        val room = QRoom.room
        val participantA = QRoomParticipant("participantA")
        val participantB = QRoomParticipant("participantB")
        return queryFactory.selectFrom(room)
            .join(participantA).on(
                participantA.room.id.eq(room.id),
                participantA.userId.eq(userIdA),
                participantA.deletedAt.isNull,
            )
            .join(participantB).on(
                participantB.room.id.eq(room.id),
                participantB.userId.eq(userIdB),
                participantB.deletedAt.isNull,
            )
            .where(
                room.type.eq(RoomType.DIRECT),
                room.deletedAt.isNull,
            )
            .fetchFirst()
    }

    override fun findMyRoomsByKeyword(userId: Long, keyword: String?): List<Room> {
        val room = QRoom.room
        val participant = QRoomParticipant.roomParticipant
        val query = queryFactory.selectFrom(room)
            .join(participant).on(
                participant.room.id.eq(room.id),
                participant.userId.eq(userId),
                participant.deletedAt.isNull,
            )
            .where(room.deletedAt.isNull)
        if (!keyword.isNullOrBlank()) {
            query.where(room.name.containsIgnoreCase(keyword))
        }
        return query.fetch()
    }
}
