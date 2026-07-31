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

    // 후보 조회(findPendingCreatedBefore)는 deletedAt.isNull로 소프트 삭제된 주문을 걸러낸다 —
    // CAS 술어도 대칭으로 걸러야 한다. 없으면 소프트 삭제된 PENDING 주문에도 UPDATE가 먼저
    // 반영되고, 뒤이은 findByIdAndDeletedAtIsNull이 null을 반환해 ResourceNotFoundException으로
    // 롤백되는 "후속 예외가 정합성을 되돌려 주는" 우회 경로에 의존하게 된다.
    override fun tryExpire(orderId: Long): Boolean {
        val affectedRows = queryFactory.update(ticketOrder)
                                       .set(ticketOrder.status, OrderStatus.CANCELLED)
                                       .set(ticketOrder.updatedAt, ZonedDateTime.now())
                                       .where(
                                           ticketOrder.id.eq(orderId),
                                           ticketOrder.status.eq(OrderStatus.PENDING),
                                           ticketOrder.deletedAt.isNull,
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
                                           ticketOrder.deletedAt.isNull,
                                       )
                                       .execute()
        return affectedRows > 0
    }
}
