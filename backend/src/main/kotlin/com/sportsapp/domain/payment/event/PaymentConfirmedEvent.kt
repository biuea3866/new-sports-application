package com.sportsapp.domain.payment.event

import com.sportsapp.domain.common.AbstractDomainEvent
import com.sportsapp.domain.payment.vo.OrderType

/**
 * 결제가 확정(승인)되었다는 과거형 사실 이벤트.
 *
 * 각 주문 컨텍스트(booking/goods/ticketing/recruitment)가 자기 EventWorker 로 구독해
 * 자신의 주문을 확정한다. payment 는 누가 구독하는지 알지 못한다.
 *
 * [orderType]/[orderId]/[paymentId] 는 직렬화 필드가 되어 구독 워커가 읽는다.
 */
class PaymentConfirmedEvent(
    val paymentId: Long,
    val orderType: OrderType,
    val orderId: Long,
) : AbstractDomainEvent(
    aggregateId = paymentId,
    topic = "payment.order-confirmed.v1",
)
