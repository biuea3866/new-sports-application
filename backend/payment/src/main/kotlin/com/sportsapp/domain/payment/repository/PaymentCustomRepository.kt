package com.sportsapp.domain.payment.repository

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
