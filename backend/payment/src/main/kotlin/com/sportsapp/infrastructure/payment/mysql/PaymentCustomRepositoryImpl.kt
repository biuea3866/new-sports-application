package com.sportsapp.infrastructure.payment.mysql

import com.querydsl.core.BooleanBuilder
import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.payment.repository.PaymentCustomRepository
import com.sportsapp.domain.payment.entity.Payment
import com.sportsapp.domain.payment.entity.PaymentStatus
import com.sportsapp.domain.payment.entity.QPayment
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@Component
class PaymentCustomRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : PaymentCustomRepository {

    override fun findUnexpirableOrderIds(orderType: OrderType, orderIds: List<Long>): Set<Long> {
        if (orderIds.isEmpty()) return emptySet()
        val payment = QPayment.payment
        return queryFactory.select(payment.orderId)
                           .from(payment)
                           .where(
                               payment.orderType.eq(orderType),
                               payment.orderId.`in`(orderIds),
                               payment.status.notIn(PaymentStatus.CANCELLED, PaymentStatus.FAILED),
                               payment.deletedAt.isNull,
                           )
                           .fetch()
                           .toSet()
    }

    override fun findByUserIdAndConditions(
        userId: Long,
        status: PaymentStatus?,
        paidAtFrom: ZonedDateTime?,
        paidAtTo: ZonedDateTime?,
        pageable: Pageable,
    ): Page<Payment> {
        val predicate = buildPredicate(userId, status, paidAtFrom, paidAtTo)
        val content = fetchContent(predicate, pageable)
        val total = fetchCount(predicate)
        return PageImpl(content, pageable, total)
    }

    private fun buildPredicate(
        userId: Long,
        status: PaymentStatus?,
        paidAtFrom: ZonedDateTime?,
        paidAtTo: ZonedDateTime?,
    ): BooleanBuilder {
        val payment = QPayment.payment
        val predicate = BooleanBuilder()
        predicate.and(payment.userId.eq(userId))
        predicate.and(payment.deletedAt.isNull)
        status?.let { predicate.and(payment.status.eq(it)) }
        paidAtFrom?.let { predicate.and(payment.paidAt.goe(it)) }
        paidAtTo?.let { predicate.and(payment.paidAt.loe(it)) }
        return predicate
    }

    private fun fetchContent(predicate: BooleanBuilder, pageable: Pageable): List<Payment> {
        val payment = QPayment.payment
        return queryFactory.selectFrom(payment)
                           .where(predicate)
                           .orderBy(payment.createdAt.desc())
                           .offset(pageable.offset)
                           .limit(pageable.pageSize.toLong())
                           .fetch()
    }

    private fun fetchCount(predicate: BooleanBuilder): Long {
        val payment = QPayment.payment
        return queryFactory.select(payment.count())
                           .from(payment)
                           .where(predicate)
                           .fetchOne() ?: 0L
    }
}
