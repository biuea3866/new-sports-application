package com.sportsapp.infrastructure.persistence.message

import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.message.QRoomParticipant
import com.sportsapp.domain.message.RoomParticipant
import com.sportsapp.domain.message.RoomParticipantCustomRepository
import org.springframework.stereotype.Component

@Component
class RoomParticipantCustomRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : RoomParticipantCustomRepository {

    override fun findActiveByRoomId(roomId: Long): List<RoomParticipant> {
        val participant = QRoomParticipant.roomParticipant
        return queryFactory.selectFrom(participant)
            .where(
                participant.room.id.eq(roomId),
                participant.deletedAt.isNull,
            )
            .fetch()
    }

    override fun existsByRoomIdAndUserId(roomId: Long, userId: Long): Boolean {
        val participant = QRoomParticipant.roomParticipant
        return queryFactory.selectFrom(participant)
            .where(
                participant.room.id.eq(roomId),
                participant.userId.eq(userId),
                participant.deletedAt.isNull,
            )
            .fetchFirst() != null
    }

    override fun findActiveByRoomIdAndUserId(roomId: Long, userId: Long): RoomParticipant? {
        val participant = QRoomParticipant.roomParticipant
        return queryFactory.selectFrom(participant)
            .where(
                participant.room.id.eq(roomId),
                participant.userId.eq(userId),
                participant.deletedAt.isNull,
            )
            .fetchFirst()
    }
}
