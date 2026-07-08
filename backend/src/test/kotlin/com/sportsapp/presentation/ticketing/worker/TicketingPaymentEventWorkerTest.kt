package com.sportsapp.presentation.ticketing.worker

import com.sportsapp.application.ticketing.usecase.CancelTicketingPaymentUseCase
import com.sportsapp.application.ticketing.usecase.ConfirmTicketingPaymentUseCase
import com.sportsapp.domain.payment.event.PaymentCancelledEvent
import com.sportsapp.domain.payment.event.PaymentConfirmedEvent
import com.sportsapp.domain.payment.vo.OrderType
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify

class TicketingPaymentEventWorkerTest : BehaviorSpec({

    Given("확정 워커") {
        val confirmUseCase = mockk<ConfirmTicketingPaymentUseCase>()
        val worker = PaymentConfirmedEventWorker(confirmUseCase)
        justRun { confirmUseCase.execute(any(), any()) }

        When("TICKETING 확정 이벤트를 수신하면") {
            worker.consume(PaymentConfirmedEvent(paymentId = 300L, orderType = OrderType.TICKETING, orderId = 30L))

            Then("자기 확정 UseCase 에 위임한다") {
                verify(exactly = 1) { confirmUseCase.execute(30L, 300L) }
            }
        }

        When("다른 타입(RECRUITMENT) 확정 이벤트를 수신하면") {
            worker.consume(PaymentConfirmedEvent(paymentId = 400L, orderType = OrderType.RECRUITMENT, orderId = 40L))

            Then("무시하고 UseCase 를 호출하지 않는다") {
                verify(exactly = 0) { confirmUseCase.execute(40L, 400L) }
            }
        }
    }

    Given("취소 워커") {
        val cancelUseCase = mockk<CancelTicketingPaymentUseCase>()
        val worker = PaymentCancelledEventWorker(cancelUseCase)
        justRun { cancelUseCase.execute(any()) }

        When("TICKETING 취소 이벤트를 수신하면") {
            worker.consume(PaymentCancelledEvent(paymentId = 300L, orderType = OrderType.TICKETING, orderId = 30L))

            Then("자기 취소 UseCase 에 위임한다") {
                verify(exactly = 1) { cancelUseCase.execute(30L) }
            }
        }
    }
})
