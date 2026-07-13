package com.sportsapp.presentation.ticketing.worker

import com.sportsapp.application.ticketing.usecase.CancelTicketingPaymentUseCase
import com.sportsapp.application.ticketing.usecase.ConfirmTicketingPaymentUseCase
import com.sportsapp.domain.payment.event.PaymentEvent
import com.sportsapp.domain.common.order.OrderType
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * payment 가 발행한 단일 결제 이벤트 토픽을 구독해 자기(ticketing) 주문만 확정/취소한다.
 * 같은 토픽을 여러 컨텍스트가 구독하므로 고유 groupId 로 그룹을 분리한다.
 * 멱등은 하류 UseCase/DomainService 가 보장한다(이미 확정/취소된 주문 재수신 시 no-op).
 */
@Component
class TicketingPaymentEventWorker(
    private val confirmTicketingPaymentUseCase: ConfirmTicketingPaymentUseCase,
    private val cancelTicketingPaymentUseCase: CancelTicketingPaymentUseCase,
) {
    @KafkaListener(
        topics = [PaymentEvent.TOPIC],
        groupId = "ticketing-payment",
        containerFactory = "paymentEventKafkaListenerContainerFactory",
    )
    fun consume(event: PaymentEvent) {
        if (event.orderType != OrderType.TICKETING) return
        when (event) {
            is PaymentEvent.Confirmed -> confirmTicketingPaymentUseCase.execute(event.orderId, event.paymentId)
            is PaymentEvent.Cancelled -> cancelTicketingPaymentUseCase.execute(event.orderId)
        }
    }
}
