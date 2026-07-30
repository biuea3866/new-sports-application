package com.sportsapp.infrastructure.message.mysql

import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.message.entity.QRoomParticipant
import com.sportsapp.domain.message.entity.RoomParticipant
import com.sportsapp.domain.message.repository.RoomParticipantCustomRepository
import com.sportsapp.domain.message.vo.ParticipantType
import java.time.ZonedDateTime
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

    override fun findExpiredGuestsBefore(threshold: ZonedDateTime): List<RoomParticipant> {
        val participant = QRoomParticipant.roomParticipant
        return queryFactory.selectFrom(participant)
            .where(
                participant.participantType.eq(ParticipantType.GUEST),
                participant.expiresAt.isNotNull,
                participant.expiresAt.before(threshold),
                participant.deletedAt.isNull,
            )
            .fetch()
    }

    override fun findActiveByUserId(userId: Long): List<RoomParticipant> {
        val participant = QRoomParticipant.roomParticipant
        return queryFactory.selectFrom(participant)
            .where(
                participant.userId.eq(userId),
                participant.deletedAt.isNull,
            )
            .fetch()
    }
}
