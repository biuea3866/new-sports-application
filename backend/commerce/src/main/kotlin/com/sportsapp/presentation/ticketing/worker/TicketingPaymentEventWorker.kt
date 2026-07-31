package com.sportsapp.presentation.ticketing.worker

import com.sportsapp.application.ticketing.usecase.CancelTicketingPaymentUseCase
import com.sportsapp.application.ticketing.usecase.ConfirmTicketingPaymentUseCase
import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.payment.event.PaymentEvent
import com.sportsapp.domain.ticketing.exception.InvalidOrderStateException
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * payment 가 발행한 단일 결제 이벤트 토픽을 구독해 자기(ticketing) 주문만 확정/취소한다.
 * 같은 토픽을 여러 컨텍스트가 구독하므로 고유 groupId 로 그룹을 분리한다.
 * 멱등은 하류 UseCase/DomainService 가 보장한다(이미 확정/취소된 주문 재수신 시 no-op).
 *
 * 만료 스위퍼가 payment liveness를 읽은 직후 결제가 COMPLETED로 확정되는 좁은 레이스가
 * 발생하면, 이미 CANCELLED로 전이된 주문에 확정 이벤트가 도달해
 * [com.sportsapp.domain.ticketing.service.TicketingDomainService.confirmOrder]가
 * [InvalidOrderStateException]을 던진다 — "결제됨 + 티켓 미발급" 상태다. 이 예외는 재시도로
 * 복구되지 않는 영구 상태(CANCELLED→CONFIRMED 전이는 항상 거부)라 좁게 잡아 삼키고
 * 경보 지표만 올린다(fail-open, 컨슈머 생존 유지). 그 외 예외는 그대로 전파해
 * [com.sportsapp.infrastructure.config.KafkaConsumerConfig]의 재시도(backoff)·recoverer
 * 경로를 그대로 탄다.
 */
@Component
class TicketingPaymentEventWorker(
    private val confirmTicketingPaymentUseCase: ConfirmTicketingPaymentUseCase,
    private val cancelTicketingPaymentUseCase: CancelTicketingPaymentUseCase,
    private val meterRegistry: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(TicketingPaymentEventWorker::class.java)

    @KafkaListener(
        topics = [PaymentEvent.TOPIC],
        groupId = "ticketing-payment",
        containerFactory = "paymentEventKafkaListenerContainerFactory",
    )
    fun consume(event: PaymentEvent) {
        if (event.orderType != OrderType.TICKETING) return
        when (event) {
            is PaymentEvent.Confirmed -> handleConfirmed(event)
            is PaymentEvent.Cancelled -> cancelTicketingPaymentUseCase.execute(event.orderId)
        }
    }

    private fun handleConfirmed(event: PaymentEvent.Confirmed) {
        try {
            confirmTicketingPaymentUseCase.execute(event.orderId, event.paymentId)
        } catch (exception: InvalidOrderStateException) {
            log.error(
                "TicketingPaymentEventWorker: 종결 상태(CANCELLED 등) 주문에 결제 확정 이벤트 도달 " +
                    "— 결제됨+티켓미발급 가능성. orderId={}, paymentId={}",
                event.orderId,
                event.paymentId,
                exception,
            )
            meterRegistry.counter(CONFIRM_AFTER_TERMINAL_COUNTER).increment()
        }
    }

    companion object {
        private const val CONFIRM_AFTER_TERMINAL_COUNTER = "ticketing_confirm_after_terminal_total"
    }
}
