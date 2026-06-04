package com.sportsapp.infrastructure.persistence.message

import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.message.MessageCustomRepository
import com.sportsapp.domain.message.Message
import com.sportsapp.domain.message.QMessage
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@Component
class MessageCustomRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : MessageCustomRepository {

    override fun findByRoomIdAndNotDeleted(roomId: Long): List<Message> {
        val message = QMessage.message
        return queryFactory.selectFrom(message)
            .where(
                message.room.id.eq(roomId),
                message.deletedAt.isNull,
            )
            .fetch()
    }

    override fun findByCursor(roomId: Long, before: ZonedDateTime?, pageSize: Int): List<Message> {
        val message = QMessage.message
        val query = queryFactory.selectFrom(message)
            .where(
                message.room.id.eq(roomId),
                message.deletedAt.isNull,
            )
        if (before != null) {
            query.where(message.createdAt.lt(before))
        }
        return query.orderBy(message.createdAt.desc())
            .limit(pageSize.toLong())
            .fetch()
    }

    override fun softDeleteAllByRoomId(roomId: Long, userId: Long?) {
        val message = QMessage.message
        queryFactory.update(message)
            .set(message.deletedAt, ZonedDateTime.now())
            .set(message.deletedBy, userId)
            .where(
                message.room.id.eq(roomId),
                message.deletedAt.isNull,
            )
            .execute()
    }
}
