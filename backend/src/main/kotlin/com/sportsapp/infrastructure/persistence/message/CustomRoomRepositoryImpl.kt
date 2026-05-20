package com.sportsapp.infrastructure.persistence.message

import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.message.CustomRoomRepository
import com.sportsapp.domain.message.QRoom
import com.sportsapp.domain.message.QRoomParticipant
import com.sportsapp.domain.message.Room
import com.sportsapp.domain.message.RoomType
import org.springframework.stereotype.Component

@Component
class CustomRoomRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : CustomRoomRepository {

    override fun findDirectRoomByParticipantIds(userIdA: Long, userIdB: Long): Room? {
        val room = QRoom.room
        val participantA = QRoomParticipant("participantA")
        val participantB = QRoomParticipant("participantB")
        return queryFactory.selectFrom(room)
            .join(participantA).on(
                participantA.roomId.eq(room.id),
                participantA.userId.eq(userIdA),
                participantA.deletedAt.isNull,
            )
            .join(participantB).on(
                participantB.roomId.eq(room.id),
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
                participant.roomId.eq(room.id),
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
