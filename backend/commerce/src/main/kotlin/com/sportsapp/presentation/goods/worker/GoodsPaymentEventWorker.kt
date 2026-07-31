package com.sportsapp.presentation.goods.worker

import com.sportsapp.application.goods.usecase.CancelGoodsPaymentUseCase
import com.sportsapp.application.goods.usecase.ConfirmGoodsPaymentUseCase
import com.sportsapp.domain.goods.exception.InvalidGoodsOrderStateException
import com.sportsapp.domain.payment.event.PaymentEvent
import com.sportsapp.domain.common.order.OrderType
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * payment 가 발행한 단일 결제 이벤트 토픽을 구독해 자기(goods) 주문만 확정/취소한다.
 * 같은 토픽을 여러 컨텍스트가 구독하므로 고유 groupId 로 그룹을 분리한다.
 * 멱등은 하류 UseCase/DomainService 가 보장한다(이미 확정/취소된 주문 재수신 시 no-op).
 *
 * W1-11a — 만료 스위퍼가 먼저 PENDING → CANCELLED로 전이한 뒤 결제 확정 이벤트가 뒤늦게
 * 도달하면 [InvalidGoodsOrderStateException]이 던져진다(CANCELLED → CONFIRMED 전이 불가).
 * 결제는 성공했는데 주문을 반영할 수 없는 상태이므로 환불 판단이 필요한 사건이다 — 컨슈머를
 * 죽이지 않고(재시도 낭비 방지) 경보 지표를 올려 운영이 인지하게 한다
 * (`facility-booking`(W1-11c)의 `BookingPaymentEventWorker`와 동일한 구조).
 */
@Component
class GoodsPaymentEventWorker(
    private val confirmGoodsPaymentUseCase: ConfirmGoodsPaymentUseCase,
    private val cancelGoodsPaymentUseCase: CancelGoodsPaymentUseCase,
    private val meterRegistry: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(GoodsPaymentEventWorker::class.java)

    @KafkaListener(
        topics = [PaymentEvent.TOPIC],
        groupId = "goods-payment",
        containerFactory = "paymentEventKafkaListenerContainerFactory",
    )
    fun consume(event: PaymentEvent) {
        if (event.orderType != OrderType.GOODS) return
        when (event) {
            is PaymentEvent.Confirmed -> confirmOrAlert(event)
            is PaymentEvent.Cancelled -> cancelGoodsPaymentUseCase.execute(event.orderId)
        }
    }

    private fun confirmOrAlert(event: PaymentEvent.Confirmed) {
        try {
            confirmGoodsPaymentUseCase.execute(event.orderId, event.paymentId)
        } catch (exception: InvalidGoodsOrderStateException) {
            log.error(
                "결제 확정 이벤트가 이미 종료된 주문에 도달했습니다 — 환불 판단 필요. orderId={} paymentId={}",
                event.orderId,
                event.paymentId,
                exception,
            )
            meterRegistry.counter(CONFIRM_AFTER_TERMINAL_COUNTER).increment()
        }
    }

    companion object {
        private const val CONFIRM_AFTER_TERMINAL_COUNTER = "goods_confirm_after_terminal_total"
    }
}
