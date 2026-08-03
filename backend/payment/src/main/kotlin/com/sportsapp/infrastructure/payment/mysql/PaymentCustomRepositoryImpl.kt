package com.sportsapp.infrastructure.payment.mysql

import com.querydsl.core.BooleanBuilder
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.common.order.OrderRef
import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.payment.dto.PaymentLivenessQueryResult
import com.sportsapp.domain.payment.dto.PaymentLivenessRow
import com.sportsapp.domain.payment.repository.PaymentCustomRepository
import com.sportsapp.domain.payment.entity.Payment
import com.sportsapp.domain.payment.entity.PaymentStatus
import com.sportsapp.domain.payment.entity.QPayment
import com.sportsapp.domain.payment.service.PaymentLivenessClassifier
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@Component
class PaymentCustomRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : PaymentCustomRepository {

    /**
     * orderType·orderIds에 해당하는 payment 행을 orderId·status·createdAt 값 객체
     * ([PaymentLivenessRow])로 전량 조회한 뒤, settled/live/attempting/none 판정과 앵커
     * (최댓값) 산출([PaymentLivenessClassifier])은 도메인 순수 함수에 위임한다 — SQL 조건과
     * 판정 로직을 이중으로 유지하면(한쪽만 고쳐 드리프트) 재발 위험이 있으므로 이 메서드는
     * 매핑·위임만 한다(no-business-flow-in-infra). status로 미리 필터링하지 않고 PENDING을
     * 포함한 전 상태를 가져온다 — Attempting(PENDING) 분류에 필요하다.
     */
    override fun findPaymentLiveness(orderType: OrderType, orderIds: List<Long>): PaymentLivenessQueryResult {
        if (orderIds.isEmpty()) return PaymentLivenessQueryResult.empty()
        val payment = QPayment.payment
        val rows = queryFactory.select(
            Projections.constructor(
                PaymentLivenessRow::class.java,
                payment.orderId,
                payment.status,
                payment.createdAt,
            )
        )
                               .from(payment)
                               .where(
                                   payment.orderType.eq(orderType),
                                   payment.orderId.`in`(orderIds),
                                   payment.deletedAt.isNull,
                               )
                               .fetch()
        return PaymentLivenessClassifier.classify(rows)
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

    override fun findByOrderRefs(
        orderRefs: List<OrderRef>,
        status: PaymentStatus?,
        paidAtFrom: ZonedDateTime?,
        paidAtTo: ZonedDateTime?,
        pageable: Pageable,
    ): Page<Payment> {
        // 참조가 비면 아래 OR 누적이 빈 조건이 되어 전체 결제가 노출된다 — 빈 페이지로 막는다.
        if (orderRefs.isEmpty()) return PageImpl(emptyList(), pageable, 0L)
        val predicate = buildOrderRefPredicate(orderRefs, status, paidAtFrom, paidAtTo)
        return PageImpl(fetchContent(predicate, pageable), pageable, fetchCount(predicate))
    }

    private fun buildOrderRefPredicate(
        orderRefs: List<OrderRef>,
        status: PaymentStatus?,
        paidAtFrom: ZonedDateTime?,
        paidAtTo: ZonedDateTime?,
    ): BooleanBuilder {
        val payment = QPayment.payment
        // 유형별로 묶어 `(type = ? AND orderId IN (...))` 를 OR로 잇는다. 유형을 빼고 id만
        // IN하면 유형이 다른 주문끼리 id가 겹쳐 남의 결제가 섞인다.
        val orderIdsByType = orderRefs.groupBy({ it.orderType }, { it.orderId })
        val orderRefPredicate = BooleanBuilder()
        orderIdsByType.forEach { (orderType, orderIds) ->
            orderRefPredicate.or(payment.orderType.eq(orderType).and(payment.orderId.`in`(orderIds)))
        }

        val predicate = BooleanBuilder()
        predicate.and(orderRefPredicate)
        predicate.and(payment.deletedAt.isNull)
        status?.let { predicate.and(payment.status.eq(it)) }
        paidAtFrom?.let { predicate.and(payment.paidAt.goe(it)) }
        paidAtTo?.let { predicate.and(payment.paidAt.loe(it)) }
        return predicate
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
