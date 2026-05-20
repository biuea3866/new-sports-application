package com.sportsapp.infrastructure.notification

import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.notification.CustomNotificationRepository
import com.sportsapp.domain.notification.Notification
import com.sportsapp.domain.notification.QNotification
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

@Component
class CustomNotificationRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : CustomNotificationRepository {

    override fun findByUserIdPaged(userId: Long, onlyUnread: Boolean, pageable: Pageable): Page<Notification> {
        val notification = QNotification.notification
        val baseCondition = notification.userId.eq(userId).and(notification.deletedAt.isNull)
        val condition = if (onlyUnread) baseCondition.and(notification.readAt.isNull) else baseCondition

        val content = queryFactory.selectFrom(notification)
                                  .where(condition)
                                  .orderBy(notification.createdAt.desc())
                                  .offset(pageable.offset)
                                  .limit(pageable.pageSize.toLong())
                                  .fetch()

        val total = queryFactory.select(notification.count())
                                .from(notification)
                                .where(condition)
                                .fetchOne() ?: 0L

        return PageImpl(content, pageable, total)
    }
}
