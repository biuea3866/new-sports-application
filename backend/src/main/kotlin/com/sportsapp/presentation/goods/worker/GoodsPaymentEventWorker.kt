package com.sportsapp.presentation.goods.worker

import com.sportsapp.application.goods.usecase.CancelGoodsPaymentUseCase
import com.sportsapp.application.goods.usecase.ConfirmGoodsPaymentUseCase
import com.sportsapp.domain.payment.event.PaymentEvent
import com.sportsapp.domain.common.order.OrderType
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * payment 가 발행한 단일 결제 이벤트 토픽을 구독해 자기(goods) 주문만 확정/취소한다.
 * 같은 토픽을 여러 컨텍스트가 구독하므로 고유 groupId 로 그룹을 분리한다.
 * 멱등은 하류 UseCase/DomainService 가 보장한다(이미 확정/취소된 주문 재수신 시 no-op).
 */
@Component
class GoodsPaymentEventWorker(
    private val confirmGoodsPaymentUseCase: ConfirmGoodsPaymentUseCase,
    private val cancelGoodsPaymentUseCase: CancelGoodsPaymentUseCase,
) {
    @KafkaListener(topics = [PaymentEvent.TOPIC], groupId = "goods-payment")
    fun consume(event: PaymentEvent) {
        if (event.orderType != OrderType.GOODS) return
        when (event) {
            is PaymentEvent.Confirmed -> confirmGoodsPaymentUseCase.execute(event.orderId, event.paymentId)
            is PaymentEvent.Cancelled -> cancelGoodsPaymentUseCase.execute(event.orderId)
        }
    }
}
