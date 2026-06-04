package com.sportsapp.infrastructure.notification.mysql
import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.notification.repository.NotificationCustomRepository
import com.sportsapp.domain.notification.entity.Notification
import com.sportsapp.domain.notification.entity.QNotification
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

@Component
class NotificationCustomRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : NotificationCustomRepository {

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
