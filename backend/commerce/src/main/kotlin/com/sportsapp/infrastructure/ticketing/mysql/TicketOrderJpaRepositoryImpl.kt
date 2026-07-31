package com.sportsapp.infrastructure.ticketing.mysql

import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.ticketing.dto.TicketOrderExpiryCandidate
import com.sportsapp.domain.ticketing.entity.OrderStatus
import com.sportsapp.domain.ticketing.entity.QTicketOrder.ticketOrder
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import java.time.ZonedDateTime

/**
 * [EventJpaRepositoryImpl]과 동일한 명명 규칙 프래그먼트 구현체 — Spring Data JPA가
 * `TicketOrderJpaRepository`(인터페이스명) + `Impl` 접미사로 이 클래스를 자동 배선한다.
 * W1-11b 만료 스위퍼의 청크 조회·CAS 전이(tryExpire/tryConfirm)를 QueryDSL로 구현한다 —
 * booking(W1-11c) [com.sportsapp.infrastructure.booking.mysql.BookingJpaRepositoryImpl]과
 * 동일한 CAS 패턴(조건부 UPDATE, WHERE status='PENDING').
 */
class TicketOrderJpaRepositoryImpl : TicketOrderQueryDslRepository {

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    private val queryFactory: JPAQueryFactory
        get() = JPAQueryFactory(entityManager)

    override fun findPendingCreatedBefore(before: ZonedDateTime, afterId: Long, limit: Int): List<TicketOrderExpiryCandidate> {
        return queryFactory.select(Projections.constructor(TicketOrderExpiryCandidate::class.java, ticketOrder.id, ticketOrder.createdAt))
                           .from(ticketOrder)
                           .where(
                               ticketOrder.status.eq(OrderStatus.PENDING),
                               ticketOrder.createdAt.lt(before),
                               ticketOrder.id.gt(afterId),
                               ticketOrder.deletedAt.isNull,
                           )
                           .orderBy(ticketOrder.id.asc())
                           .limit(limit.toLong())
                           .fetch()
    }

    override fun tryExpire(orderId: Long): Boolean {
        val affectedRows = queryFactory.update(ticketOrder)
                                       .set(ticketOrder.status, OrderStatus.CANCELLED)
                                       .set(ticketOrder.updatedAt, ZonedDateTime.now())
                                       .where(
                                           ticketOrder.id.eq(orderId),
                                           ticketOrder.status.eq(OrderStatus.PENDING),
                                       )
                                       .execute()
        return affectedRows > 0
    }

    override fun tryConfirm(orderId: Long, paymentId: Long): Boolean {
        val affectedRows = queryFactory.update(ticketOrder)
                                       .set(ticketOrder.status, OrderStatus.CONFIRMED)
                                       .set(ticketOrder.paymentId, paymentId)
                                       .set(ticketOrder.updatedAt, ZonedDateTime.now())
                                       .where(
                                           ticketOrder.id.eq(orderId),
                                           ticketOrder.status.eq(OrderStatus.PENDING),
                                       )
                                       .execute()
        return affectedRows > 0
    }
}
