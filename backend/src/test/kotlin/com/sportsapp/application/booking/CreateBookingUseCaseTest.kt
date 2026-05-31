package com.sportsapp.application.booking

import com.sportsapp.domain.booking.BookingDomainService
import com.sportsapp.domain.booking.BookingResult
import com.sportsapp.domain.booking.BookingStatus
import com.sportsapp.domain.booking.SlotBusyException
import com.sportsapp.domain.payment.OrderType
import com.sportsapp.domain.payment.Payment
import com.sportsapp.domain.payment.PaymentDomainService
import com.sportsapp.domain.payment.PaymentMethod
import com.sportsapp.domain.payment.PaymentStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal

class CreateBookingUseCaseTest : BehaviorSpec({

    val bookingDomainService = mockk<BookingDomainService>()
    val paymentDomainService = mockk<PaymentDomainService>()
    val useCase = CreateBookingUseCase(bookingDomainService, paymentDomainService)

    val command = CreateBookingCommand(
        userId = 1L,
        slotId = 42L,
        paymentMethod = PaymentMethod.CREDIT_CARD,
        amount = BigDecimal("50000"),
        currency = "KRW",
    )

    Given("BookingDomainService 만 호출하는 정상 흐름") {
        val bookingResult = BookingResult(
            bookingId = 10L,
            slotId = 42L,
            userId = 1L,
            status = BookingStatus.PENDING,
        )
        val payment = mockk<Payment> {
            every { id } returns 99L
            every { status } returns PaymentStatus.PENDING
            every { paidAt } returns null
            every { failureReason } returns null
        }
        every { bookingDomainService.requestBooking(1L, 42L) } returns bookingResult
        every {
            paymentDomainService.create(
                userId = 1L,
                idempotencyKey = any(),
                orderType = OrderType.BOOKING,
                orderId = 10L,
                method = PaymentMethod.CREDIT_CARD,
                amount = BigDecimal("50000"),
                currency = "KRW",
            )
        } returns payment

        When("execute 를 호출하면") {
            val result = useCase.execute(command)

            Then("CreateBookingResult 에 bookingId 와 paymentId 가 담긴다") {
                result.bookingId shouldBe 10L
                result.paymentId shouldBe 99L
                result.status shouldBe BookingStatus.PENDING
            }

            Then("DomainService 만 호출하고 Repository 를 직접 참조하지 않는다") {
                verify(exactly = 1) { bookingDomainService.requestBooking(1L, 42L) }
                verify(exactly = 1) { paymentDomainService.create(any(), any(), any(), any(), any(), any(), any()) }
            }
        }
    }

    Given("락 획득 실패 흐름") {
        val localBookingService = mockk<BookingDomainService>()
        val localPaymentService = mockk<PaymentDomainService>()
        val localUseCase = CreateBookingUseCase(localBookingService, localPaymentService)
        every { localBookingService.requestBooking(1L, 42L) } throws SlotBusyException(42L)

        When("execute 를 호출하면") {
            Then("SlotBusyException 이 전파되고 PaymentDomainService 는 호출되지 않는다") {
                shouldThrow<SlotBusyException> {
                    localUseCase.execute(command)
                }
                verify(exactly = 0) { localPaymentService.create(any(), any(), any(), any(), any(), any(), any()) }
            }
        }
    }
})
