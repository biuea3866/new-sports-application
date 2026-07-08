package com.sportsapp.presentation.booking.worker

import com.sportsapp.application.booking.usecase.ConfirmBookingPaymentUseCase
import com.sportsapp.domain.payment.event.PaymentConfirmedEvent
import com.sportsapp.domain.payment.vo.OrderType
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * payment 가 발행한 확정 이벤트를 구독해 자기(booking) 주문만 확정한다.
 * 같은 토픽을 여러 컨텍스트가 구독하므로 고유 groupId 로 그룹을 분리한다.
 */
@Component
class PaymentConfirmedEventWorker(
    private val confirmBookingPaymentUseCase: ConfirmBookingPaymentUseCase,
) {
    @KafkaListener(topics = ["event.payment.order-confirmed"], groupId = "booking-payment-order")
    fun consume(event: PaymentConfirmedEvent) {
        if (event.orderType != OrderType.BOOKING) return
        confirmBookingPaymentUseCase.execute(event.orderId, event.paymentId)
    }
}
