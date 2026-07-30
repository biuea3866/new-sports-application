package com.sportsapp.infrastructure.ticketing.mysql

import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.ticketing.entity.EventStatus
import com.sportsapp.domain.ticketing.entity.QEvent.event
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext

class EventJpaRepositoryImpl : EventQueryDslRepository {

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    private val queryFactory: JPAQueryFactory
        get() = JPAQueryFactory(entityManager)

    override fun countByOwnerIdGroupByStatus(ownerId: Long): Map<EventStatus, Long> {
        val rows = queryFactory.select(event.status, event.count())
            .from(event)
            .where(
                event.ownerId.eq(ownerId),
                event.deletedAt.isNull,
            )
            .groupBy(event.status)
            .fetch()

        return rows.associate { tuple ->
            val status: EventStatus = requireNotNull(tuple.get(event.status)) { "status must not be null" }
            val count: Long = tuple.get(event.count()) ?: 0L
            status to count
        }
    }
}
