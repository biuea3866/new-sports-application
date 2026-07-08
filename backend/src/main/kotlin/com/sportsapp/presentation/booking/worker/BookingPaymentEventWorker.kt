package com.sportsapp.presentation.booking.worker

import com.sportsapp.application.booking.usecase.CancelBookingPaymentUseCase
import com.sportsapp.application.booking.usecase.ConfirmBookingPaymentUseCase
import com.sportsapp.domain.payment.event.PaymentEvent
import com.sportsapp.domain.payment.vo.OrderType
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * payment 가 발행한 단일 결제 이벤트 토픽을 구독해 자기(booking) 주문만 확정/취소한다.
 * 같은 토픽을 여러 컨텍스트가 구독하므로 고유 groupId 로 그룹을 분리한다.
 * 멱등은 하류 UseCase/DomainService 가 보장한다(이미 확정/취소된 주문 재수신 시 no-op).
 */
@Component
class BookingPaymentEventWorker(
    private val confirmBookingPaymentUseCase: ConfirmBookingPaymentUseCase,
    private val cancelBookingPaymentUseCase: CancelBookingPaymentUseCase,
) {
    @KafkaListener(topics = [PaymentEvent.TOPIC], groupId = "booking-payment")
    fun consume(event: PaymentEvent) {
        if (event.orderType != OrderType.BOOKING) return
        when (event) {
            is PaymentEvent.Confirmed -> confirmBookingPaymentUseCase.execute(event.orderId, event.paymentId)
            is PaymentEvent.Cancelled -> cancelBookingPaymentUseCase.execute(event.orderId)
        }
    }
}
