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
     * 만료 스위퍼(W1-11a~d 공통 결제 성공 가드)가 소비한다 — orderType·orderId 목록 중
     * COMPLETED 상태인 orderId만 반환한다. Payment 엔티티를 소비 컨텍스트에 노출하지 않는다.
     */
    fun findCompletedOrderIds(orderType: OrderType, orderIds: List<Long>): Set<Long>
}
