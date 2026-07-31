package com.sportsapp.domain.payment.repository

import com.sportsapp.domain.common.order.OrderType
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
     * 만료 스위퍼(W1-11a~d 공통 만료 금지 가드)가 소비한다 — orderType·orderId 목록 중
     * "만료시키면 안 되는" 주문의 orderId만 반환한다. PaymentStatus 전이는
     * PENDING → READY → COMPLETED(→ REFUNDED)이고, 결제 개시 시점에 이미 PENDING 행이
     * 생성되므로 사용자가 PG 결제창에 있는 동안(READY)도 만료 금지 대상이다 — COMPLETED만
     * 보면 결제 진행 중인 예약이 새어나가 "돈은 받고 서비스는 없는" 상태가 된다.
     * CANCELLED·FAILED(명확히 종료된 결제)만 만료를 허용하고, 그 외 전 상태(PENDING/READY/
     * COMPLETED/REFUNDED)는 만료 금지로 취급한다. Payment 엔티티를 소비 컨텍스트에 노출하지 않는다.
     */
    fun findUnexpirableOrderIds(orderType: OrderType, orderIds: List<Long>): Set<Long>
}
