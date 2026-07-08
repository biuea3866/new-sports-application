package com.sportsapp.presentation.ticketing.worker

import com.sportsapp.application.ticketing.usecase.CancelTicketingPaymentUseCase
import com.sportsapp.application.ticketing.usecase.ConfirmTicketingPaymentUseCase
import com.sportsapp.domain.payment.event.PaymentEvent
import com.sportsapp.domain.payment.vo.OrderType
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify

class TicketingPaymentEventWorkerTest : BehaviorSpec({

    val confirmUseCase = mockk<ConfirmTicketingPaymentUseCase>()
    val cancelUseCase = mockk<CancelTicketingPaymentUseCase>()
    val worker = TicketingPaymentEventWorker(confirmUseCase, cancelUseCase)
    justRun { confirmUseCase.execute(any(), any()) }
    justRun { cancelUseCase.execute(any()) }

    Given("결제 이벤트 워커") {
        When("TICKETING 확정 이벤트를 수신하면") {
            worker.consume(PaymentEvent.Confirmed(paymentId = 300L, orderType = OrderType.TICKETING, orderId = 30L))

            Then("자기 확정 UseCase 에 위임한다") {
                verify(exactly = 1) { confirmUseCase.execute(30L, 300L) }
            }
        }

        When("TICKETING 취소 이벤트를 수신하면") {
            worker.consume(PaymentEvent.Cancelled(paymentId = 300L, orderType = OrderType.TICKETING, orderId = 30L))

            Then("자기 취소 UseCase 에 위임한다") {
                verify(exactly = 1) { cancelUseCase.execute(30L) }
            }
        }

        When("다른 타입(RECRUITMENT) 확정 이벤트를 수신하면") {
            worker.consume(PaymentEvent.Confirmed(paymentId = 400L, orderType = OrderType.RECRUITMENT, orderId = 40L))

            Then("무시하고 어떤 UseCase 도 호출하지 않는다") {
                verify(exactly = 0) { confirmUseCase.execute(40L, 400L) }
            }
        }
    }
})
