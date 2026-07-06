package com.sportsapp.infrastructure.message.mysql

import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.message.entity.QMessage
import com.sportsapp.domain.message.entity.QRoom
import com.sportsapp.domain.message.entity.QRoomParticipant
import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.repository.RoomCustomRepository
import com.sportsapp.domain.message.vo.QRoomListView
import com.sportsapp.domain.message.vo.RoomContextType
import com.sportsapp.domain.message.vo.RoomListView
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

    override fun findMyRoomViews(userId: Long, keyword: String?): List<RoomListView> {
        val room = QRoom.room
        val participant = QRoomParticipant.roomParticipant
        val message = QMessage.message
        val lastMessage = QMessage("lastMessage")

        val query = queryFactory
            .select(
                QRoomListView(
                    room.id,
                    room.type,
                    room.name,
                    room.contextType,
                    lastMessage.content,
                    lastMessage.createdAt,
                ),
            )
            .from(room)
            .join(participant).on(
                participant.room.id.eq(room.id),
                participant.userId.eq(userId),
                participant.deletedAt.isNull,
            )
            .leftJoin(lastMessage).on(
                lastMessage.room.id.eq(room.id),
                lastMessage.id.eq(
                    JPAExpressions.select(message.id.max())
                        .from(message)
                        .where(
                            message.room.id.eq(room.id),
                            message.deletedAt.isNull,
                        ),
                ),
            )
            .where(room.deletedAt.isNull)
        if (!keyword.isNullOrBlank()) {
            query.where(room.name.containsIgnoreCase(keyword))
        }
        return query.fetch()
    }

    override fun findByContext(contextType: RoomContextType, contextId: Long): Room? {
        val room = QRoom.room
        return queryFactory.selectFrom(room)
            .where(
                room.contextType.eq(contextType),
                room.contextId.eq(contextId),
                room.deletedAt.isNull,
            )
            .fetchFirst()
    }

    override fun findByContextAndParticipant(
        contextType: RoomContextType,
        contextId: Long,
        participantUserId: Long,
    ): Room? {
        val room = QRoom.room
        val participant = QRoomParticipant.roomParticipant
        return queryFactory.selectFrom(room)
            .join(participant).on(
                participant.room.id.eq(room.id),
                participant.userId.eq(participantUserId),
                participant.deletedAt.isNull,
            )
            .where(
                room.contextType.eq(contextType),
                room.contextId.eq(contextId),
                room.deletedAt.isNull,
            )
            .fetchFirst()
    }
}
