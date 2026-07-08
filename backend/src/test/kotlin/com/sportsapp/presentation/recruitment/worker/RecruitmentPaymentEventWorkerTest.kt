package com.sportsapp.presentation.recruitment.worker

import com.sportsapp.application.recruitment.usecase.CancelRecruitmentPaymentUseCase
import com.sportsapp.application.recruitment.usecase.ConfirmRecruitmentPaymentUseCase
import com.sportsapp.domain.payment.event.PaymentCancelledEvent
import com.sportsapp.domain.payment.event.PaymentConfirmedEvent
import com.sportsapp.domain.payment.vo.OrderType
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify

class RecruitmentPaymentEventWorkerTest : BehaviorSpec({

    Given("확정 워커") {
        val confirmUseCase = mockk<ConfirmRecruitmentPaymentUseCase>()
        val worker = PaymentConfirmedEventWorker(confirmUseCase)
        justRun { confirmUseCase.execute(any(), any()) }

        When("RECRUITMENT 확정 이벤트를 수신하면") {
            worker.consume(PaymentConfirmedEvent(paymentId = 400L, orderType = OrderType.RECRUITMENT, orderId = 40L))

            Then("자기 확정 UseCase 에 위임한다") {
                verify(exactly = 1) { confirmUseCase.execute(40L, 400L) }
            }
        }

        When("다른 타입(GOODS) 확정 이벤트를 수신하면") {
            worker.consume(PaymentConfirmedEvent(paymentId = 200L, orderType = OrderType.GOODS, orderId = 20L))

            Then("무시하고 UseCase 를 호출하지 않는다") {
                verify(exactly = 0) { confirmUseCase.execute(20L, 200L) }
            }
        }
    }

    Given("취소 워커") {
        val cancelUseCase = mockk<CancelRecruitmentPaymentUseCase>()
        val worker = PaymentCancelledEventWorker(cancelUseCase)
        justRun { cancelUseCase.execute(any()) }

        When("RECRUITMENT 취소 이벤트를 수신하면") {
            worker.consume(PaymentCancelledEvent(paymentId = 400L, orderType = OrderType.RECRUITMENT, orderId = 40L))

            Then("자기 취소 UseCase 에 위임한다") {
                verify(exactly = 1) { cancelUseCase.execute(40L) }
            }
        }
    }
})
