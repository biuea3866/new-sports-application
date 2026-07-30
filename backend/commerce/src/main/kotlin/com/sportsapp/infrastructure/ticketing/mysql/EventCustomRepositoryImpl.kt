package com.sportsapp.infrastructure.ticketing.mysql

import com.querydsl.core.BooleanBuilder
import com.querydsl.core.types.OrderSpecifier
import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.ticketing.repository.EventCustomRepository
import com.sportsapp.domain.ticketing.entity.Event
import com.sportsapp.domain.ticketing.dto.EventCriteria
import com.sportsapp.domain.ticketing.entity.EventStatus
import com.sportsapp.domain.ticketing.entity.QEvent
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component

@Component
class EventCustomRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : EventCustomRepository {

    override fun countByOwnerIdGroupByStatus(ownerId: Long): Map<EventStatus, Long> {
        val event = QEvent.event
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

    override fun findByCriteria(criteria: EventCriteria, pageable: Pageable): Page<Event> {
        val predicate = buildPredicate(criteria)
        val orders = buildOrderSpecifiers(pageable.sort)
        val content = fetchContent(predicate, orders, pageable)
        val total = fetchCount(predicate)
        return PageImpl(content, pageable, total)
    }

    private fun buildPredicate(criteria: EventCriteria): BooleanBuilder {
        val event = QEvent.event
        val predicate = BooleanBuilder()
        predicate.and(event.deletedAt.isNull)
        criteria.status?.let { predicate.and(event.status.eq(it)) }
        criteria.startsAtFrom?.let { predicate.and(event.startsAt.goe(it)) }
        criteria.startsAtTo?.let { predicate.and(event.startsAt.loe(it)) }
        criteria.keyword?.takeIf { it.isNotBlank() }?.let { predicate.and(event.title.contains(it)) }
        return predicate
    }

    private fun buildOrderSpecifiers(sort: Sort): List<OrderSpecifier<*>> {
        val event = QEvent.event
        val specifiers = sort.map { order ->
            val path = when (order.property) {
                "startsAt" -> event.startsAt
                "title" -> event.title
                else -> event.startsAt
            }
            if (order.direction == Sort.Direction.ASC) path.asc() else path.desc()
        }.toList()
        return specifiers.ifEmpty { listOf(event.startsAt.asc()) }
    }

    private fun fetchContent(
        predicate: BooleanBuilder,
        orders: List<OrderSpecifier<*>>,
        pageable: Pageable,
    ): List<Event> {
        val event = QEvent.event
        var query = queryFactory.selectFrom(event).where(predicate)
        orders.forEach { query = query.orderBy(it) }
        return query.offset(pageable.offset).limit(pageable.pageSize.toLong()).fetch()
    }

    private fun fetchCount(predicate: BooleanBuilder): Long {
        val event = QEvent.event
        return queryFactory.select(event.count()).from(event).where(predicate).fetchOne() ?: 0L
    }
}
