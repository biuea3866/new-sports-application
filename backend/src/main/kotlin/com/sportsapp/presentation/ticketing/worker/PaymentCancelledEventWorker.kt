package com.sportsapp.presentation.ticketing.worker

import com.sportsapp.application.ticketing.usecase.CancelTicketingPaymentUseCase
import com.sportsapp.domain.payment.event.PaymentCancelledEvent
import com.sportsapp.domain.payment.vo.OrderType
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * payment 가 발행한 취소 이벤트를 구독해 자기(ticketing) 주문만 취소한다.
 * 같은 토픽을 여러 컨텍스트가 구독하므로 고유 groupId 로 그룹을 분리한다.
 */
@Component
class PaymentCancelledEventWorker(
    private val cancelTicketingPaymentUseCase: CancelTicketingPaymentUseCase,
) {
    @KafkaListener(topics = ["event.payment.order-cancelled"], groupId = "ticketing-payment-cancel")
    fun consume(event: PaymentCancelledEvent) {
        if (event.orderType != OrderType.TICKETING) return
        cancelTicketingPaymentUseCase.execute(event.orderId)
    }
}
