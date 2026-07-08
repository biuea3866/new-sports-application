package com.sportsapp.presentation.recruitment.worker

import com.sportsapp.application.recruitment.usecase.CancelRecruitmentPaymentUseCase
import com.sportsapp.domain.payment.event.PaymentCancelledEvent
import com.sportsapp.domain.payment.vo.OrderType
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * payment 가 발행한 취소 이벤트를 구독해 자기(recruitment) PENDING 신청만 취소한다.
 * 같은 토픽을 여러 컨텍스트가 구독하므로 고유 groupId 로 그룹을 분리한다.
 */
@Component
class PaymentCancelledEventWorker(
    private val cancelRecruitmentPaymentUseCase: CancelRecruitmentPaymentUseCase,
) {
    @KafkaListener(topics = ["event.payment.order-cancelled"], groupId = "recruitment-payment-cancel")
    fun consume(event: PaymentCancelledEvent) {
        if (event.orderType != OrderType.RECRUITMENT) return
        cancelRecruitmentPaymentUseCase.execute(event.orderId)
    }
}
