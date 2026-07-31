package com.sportsapp.infrastructure.payment.mysql

import com.querydsl.core.BooleanBuilder
import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.payment.dto.PaymentLivenessQueryResult
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
     * orderType·orderIds에 해당하는 payment 행을 전량 조회한 뒤, live/settled 판정
     * ([PaymentLivenessClassifier])은 도메인 순수 함수에 위임한다 — SQL 조건과 판정 로직을
     * 이중으로 유지하면(한쪽만 고쳐 드리프트) 재발 위험이 있으므로 이 메서드는 매핑·위임만
     * 한다(no-business-flow-in-infra). 한 주문에 payment 행이 여러 건이어도(재시도로
     * idempotencyKey를 바꿔 재개시한 경우 등) 그중 하나라도 해당 판정이면 그 orderId는
     * 결과 Set에 포함된다.
     */
    override fun findPaymentLiveness(orderType: OrderType, orderIds: List<Long>): PaymentLivenessQueryResult {
        if (orderIds.isEmpty()) return PaymentLivenessQueryResult.empty()
        val payment = QPayment.payment
        val payments = queryFactory.selectFrom(payment)
                                   .where(
                                       payment.orderType.eq(orderType),
                                       payment.orderId.`in`(orderIds),
                                       payment.deletedAt.isNull,
                                   )
                                   .fetch()
        return PaymentLivenessQueryResult(
            liveOrderIds = payments.filter { PaymentLivenessClassifier.isLive(it.status) }.map { it.orderId }.toSet(),
            settledOrderIds = payments.filter { PaymentLivenessClassifier.isSettled(it.status) }.map { it.orderId }.toSet(),
        )
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
