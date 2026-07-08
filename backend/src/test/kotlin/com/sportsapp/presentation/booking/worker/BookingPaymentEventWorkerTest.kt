package com.sportsapp.presentation.booking.worker

import com.sportsapp.application.booking.usecase.CancelBookingPaymentUseCase
import com.sportsapp.application.booking.usecase.ConfirmBookingPaymentUseCase
import com.sportsapp.domain.payment.event.PaymentCancelledEvent
import com.sportsapp.domain.payment.event.PaymentConfirmedEvent
import com.sportsapp.domain.payment.vo.OrderType
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify

class BookingPaymentEventWorkerTest : BehaviorSpec({

    Given("확정 워커") {
        val confirmUseCase = mockk<ConfirmBookingPaymentUseCase>()
        val worker = PaymentConfirmedEventWorker(confirmUseCase)
        justRun { confirmUseCase.execute(any(), any()) }

        When("BOOKING 확정 이벤트를 수신하면") {
            worker.consume(PaymentConfirmedEvent(paymentId = 100L, orderType = OrderType.BOOKING, orderId = 10L))

            Then("자기 확정 UseCase 에 위임한다") {
                verify(exactly = 1) { confirmUseCase.execute(10L, 100L) }
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
        val cancelUseCase = mockk<CancelBookingPaymentUseCase>()
        val worker = PaymentCancelledEventWorker(cancelUseCase)
        justRun { cancelUseCase.execute(any()) }

        When("BOOKING 취소 이벤트를 중복 수신하면") {
            worker.consume(PaymentCancelledEvent(paymentId = 100L, orderType = OrderType.BOOKING, orderId = 10L))
            worker.consume(PaymentCancelledEvent(paymentId = 100L, orderType = OrderType.BOOKING, orderId = 10L))

            Then("매 수신마다 취소 UseCase 에 위임하고 멱등은 하류가 보장한다") {
                verify(exactly = 2) { cancelUseCase.execute(10L) }
            }
        }

        When("다른 타입(TICKETING) 취소 이벤트를 수신하면") {
            worker.consume(PaymentCancelledEvent(paymentId = 300L, orderType = OrderType.TICKETING, orderId = 30L))

            Then("무시하고 UseCase 를 호출하지 않는다") {
                verify(exactly = 0) { cancelUseCase.execute(30L) }
            }
        }
    }
})
