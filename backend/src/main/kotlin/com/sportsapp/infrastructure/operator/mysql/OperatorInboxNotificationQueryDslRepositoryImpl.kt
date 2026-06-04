package com.sportsapp.infrastructure.operator.mysql

import com.querydsl.core.BooleanBuilder
import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.operator.entity.OperatorInboxNotification
import com.sportsapp.domain.operator.entity.OperatorInboxNotificationStatus
import com.sportsapp.domain.operator.entity.QOperatorInboxNotification.operatorInboxNotification
import com.sportsapp.domain.operator.vo.OperatorInboxNotificationType
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository

@Repository
class OperatorInboxNotificationQueryDslRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : OperatorInboxNotificationQueryDslRepository {

    override fun findByRecipientPaged(
        recipientUserId: Long,
        type: OperatorInboxNotificationType?,
        status: OperatorInboxNotificationStatus?,
        pageable: Pageable,
    ): Page<OperatorInboxNotification> {
        val condition = BooleanBuilder()
            .and(operatorInboxNotification.recipientUserId.eq(recipientUserId))
            .and(operatorInboxNotification.deletedAt.isNull)

        type?.let { condition.and(operatorInboxNotification.type.eq(it)) }
        status?.let { condition.and(operatorInboxNotification.status.eq(it)) }

        val content = queryFactory.selectFrom(operatorInboxNotification)
            .where(condition)
            .offset(pageable.offset)
            .limit(pageable.pageSize.toLong())
            .orderBy(operatorInboxNotification.createdAt.desc())
            .fetch()

        val total = queryFactory.select(operatorInboxNotification.count())
            .from(operatorInboxNotification)
            .where(condition)
            .fetchOne() ?: 0L

        return PageImpl(content, pageable, total)
    }
}
