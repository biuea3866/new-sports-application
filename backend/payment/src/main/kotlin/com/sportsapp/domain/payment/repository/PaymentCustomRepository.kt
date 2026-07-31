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
     * "만료시키면 안 되는" 주문의 orderId만 반환한다.
     *
     * status만으로는 판단할 수 없다 — 결제 개시 시점에 이미 PENDING 행이 생성되므로 주문마다
     * PENDING 또는 READY 행이 항상 존재한다. `activeSince`(활동 창 시작 시각) 이후에 갱신된
     * PENDING/READY는 "사용자가 지금 결제 진행 중"으로 보아 만료 금지, 그 이전(방치)은 만료를
     * 허용한다. COMPLETED는 무조건 만료 금지, CANCELLED/FAILED/REFUNDED는 만료를 허용한다.
     * 판정 규칙 상세는 [com.sportsapp.domain.payment.service.PaymentExpiryGuard] 참고.
     * Payment 엔티티를 소비 컨텍스트에 노출하지 않는다.
     */
    fun findUnexpirableOrderIds(orderType: OrderType, orderIds: List<Long>, activeSince: ZonedDateTime): Set<Long>
}
