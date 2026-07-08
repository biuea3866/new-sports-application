package com.sportsapp.application.booking

import com.sportsapp.application.booking.usecase.CancelBookingPaymentUseCase
import com.sportsapp.application.booking.usecase.ConfirmBookingPaymentUseCase
import com.sportsapp.domain.booking.entity.Booking
import com.sportsapp.domain.booking.service.BookingDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify

class BookingPaymentUseCaseTest : BehaviorSpec({

    Given("확정 UseCase") {
        val bookingDomainService = mockk<BookingDomainService>()
        val useCase = ConfirmBookingPaymentUseCase(bookingDomainService)
        every { bookingDomainService.confirmBooking(any(), any()) } returns mockk<Booking>()

        When("execute(orderId, paymentId) 를 호출하면") {
            useCase.execute(10L, 100L)

            Then("BookingDomainService.confirmBooking 에 위임한다") {
                verify(exactly = 1) { bookingDomainService.confirmBooking(10L, 100L) }
            }
        }

        When("동일 이벤트를 2회 수신하면") {
            useCase.execute(11L, 101L)
            useCase.execute(11L, 101L)

            Then("매 수신마다 확정에 위임하고 멱등은 도메인이 보장한다") {
                verify(exactly = 2) { bookingDomainService.confirmBooking(11L, 101L) }
            }
        }
    }

    Given("취소 UseCase") {
        val bookingDomainService = mockk<BookingDomainService>()
        val useCase = CancelBookingPaymentUseCase(bookingDomainService)
        justRun { bookingDomainService.cancelPending(any()) }

        When("execute(orderId) 를 호출하면") {
            useCase.execute(20L)

            Then("BookingDomainService.cancelPending 에 위임한다") {
                verify(exactly = 1) { bookingDomainService.cancelPending(20L) }
            }
        }
    }
})
