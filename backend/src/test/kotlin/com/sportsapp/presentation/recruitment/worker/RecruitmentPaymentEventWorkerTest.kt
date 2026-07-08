package com.sportsapp.presentation.recruitment.worker

import com.sportsapp.application.recruitment.usecase.CancelRecruitmentPaymentUseCase
import com.sportsapp.application.recruitment.usecase.ConfirmRecruitmentPaymentUseCase
import com.sportsapp.domain.payment.event.PaymentEvent
import com.sportsapp.domain.payment.vo.OrderType
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify

class RecruitmentPaymentEventWorkerTest : BehaviorSpec({

    val confirmUseCase = mockk<ConfirmRecruitmentPaymentUseCase>()
    val cancelUseCase = mockk<CancelRecruitmentPaymentUseCase>()
    val worker = RecruitmentPaymentEventWorker(confirmUseCase, cancelUseCase)
    justRun { confirmUseCase.execute(any(), any()) }
    justRun { cancelUseCase.execute(any()) }

    Given("결제 이벤트 워커") {
        When("RECRUITMENT 확정 이벤트를 수신하면") {
            worker.consume(PaymentEvent.Confirmed(paymentId = 400L, orderType = OrderType.RECRUITMENT, orderId = 40L))

            Then("자기 확정 UseCase 에 위임한다") {
                verify(exactly = 1) { confirmUseCase.execute(40L, 400L) }
            }
        }

        When("RECRUITMENT 취소 이벤트를 수신하면") {
            worker.consume(PaymentEvent.Cancelled(paymentId = 400L, orderType = OrderType.RECRUITMENT, orderId = 40L))

            Then("자기 취소 UseCase 에 위임한다") {
                verify(exactly = 1) { cancelUseCase.execute(40L) }
            }
        }

        When("다른 타입(GOODS) 확정 이벤트를 수신하면") {
            worker.consume(PaymentEvent.Confirmed(paymentId = 200L, orderType = OrderType.GOODS, orderId = 20L))

            Then("무시하고 어떤 UseCase 도 호출하지 않는다") {
                verify(exactly = 0) { confirmUseCase.execute(20L, 200L) }
            }
        }
    }
})
