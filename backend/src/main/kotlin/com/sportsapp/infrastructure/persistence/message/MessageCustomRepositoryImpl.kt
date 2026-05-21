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

    override fun findByCursor(roomId: Long, before: ZonedDateTime?, pageSize: Int): List<Message> {
        val message = QMessage.message
        val query = queryFactory.selectFrom(message)
            .where(
                message.roomId.eq(roomId),
                message.deletedAt.isNull,
            )
        if (before != null) {
            query.where(message.createdAt.lt(before))
        }
        return query.orderBy(message.createdAt.desc())
            .limit(pageSize.toLong())
            .fetch()
    }
}
