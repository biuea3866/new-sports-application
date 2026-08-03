package com.sportsapp.domain.payment.repository

import com.sportsapp.domain.common.order.OrderRef
import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.payment.dto.PaymentLivenessQueryResult
import com.sportsapp.domain.payment.entity.Payment
import com.sportsapp.domain.payment.entity.PaymentStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.ZonedDateTime

interface PaymentCustomRepository {
    fun findByUserIdAndConditions(
        userId: Long,
        status: PaymentStatus?,
        paidAtFrom: ZonedDateTime?,
        paidAtTo: ZonedDateTime?,
        pageable: Pageable,
    ): Page<Payment>

    /**
     * 주문 참조 목록으로 결제를 조회한다 — 포털 판매자 매출 내역이 소비한다.
     *
     * payment는 판매자를 모른다(주문 컨텍스트 역참조 금지). "내 주문이 무엇인가"는 각 주문
     * 컨텍스트가 답하고, 그 결과를 [OrderRef] 목록으로 받아 결제 행만 돌려주는 방향이라
     * 의존 방향이 뒤집히지 않는다.
     *
     * 참조 목록이 비면 조건 없는 전체 조회가 되어 남의 결제까지 노출되므로 **빈 페이지**를
     * 반환한다(권한 누수 방지).
     */
    fun findByOrderRefs(
        orderRefs: List<OrderRef>,
        status: PaymentStatus?,
        paidAtFrom: ZonedDateTime?,
        paidAtTo: ZonedDateTime?,
        pageable: Pageable,
    ): Page<Payment>

    /**
     * 만료 스위퍼(W1-11a~d 공통 만료 금지 가드)가 소비한다 — orderType·orderId 목록에 대해
     * orderId별 결제 생존 판정([com.sportsapp.domain.common.payment.OrderPaymentLiveness])을
     * 반환한다.
     *
     * 6차 재설계: payment는 "결제가 어느 상태인가"라는 **사실**과 그 시각(발급/시도 시각)만
     * 답하고, TTL(시간 창) 정책은 호출 컨텍스트(booking 등)가 소유한다 — 그래서 TTL 분값 자체는
     * 받지 않는다. 판정 규칙 상세는
     * [com.sportsapp.domain.payment.service.PaymentLivenessClassifier] 참고. Payment
     * 엔티티·PaymentStatus enum을 소비 컨텍스트에 노출하지 않는다.
     */
    fun findPaymentLiveness(orderType: OrderType, orderIds: List<Long>): PaymentLivenessQueryResult
}
