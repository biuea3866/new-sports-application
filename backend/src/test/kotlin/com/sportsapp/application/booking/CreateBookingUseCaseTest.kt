package com.sportsapp.application.booking

import com.sportsapp.domain.booking.Booking
import com.sportsapp.domain.booking.BookingDomainService
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
import java.time.ZonedDateTime

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

    Given("[U-04] BookingDomainService만 호출하는 정상 흐름") {
        val booking = mockk<Booking> {
            every { id } returns 10L
            every { slotId } returns 42L
            every { userId } returns 1L
            every { status } returns BookingStatus.PENDING
            every { paymentId } returns null
        }
        val payment = mockk<Payment> {
            every { id } returns 99L
            every { status } returns PaymentStatus.PENDING
            every { paidAt } returns null
            every { failureReason } returns null
        }
        every { bookingDomainService.requestBooking(1L, 42L) } returns booking
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

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("[U-04] CreateBookingResult에 bookingId와 paymentId가 담긴다") {
                result.bookingId shouldBe 10L
                result.paymentId shouldBe 99L
                result.status shouldBe BookingStatus.PENDING
            }

            Then("[U-04] DomainService만 호출하고 Repository를 직접 참조하지 않는다") {
                verify(exactly = 1) { bookingDomainService.requestBooking(1L, 42L) }
                verify(exactly = 1) { paymentDomainService.create(any(), any(), any(), any(), any(), any(), any()) }
            }
        }
    }

    Given("[U-01] 락 획득 실패 흐름") {
        val localBookingService = mockk<BookingDomainService>()
        val localPaymentService = mockk<PaymentDomainService>()
        val localUseCase = CreateBookingUseCase(localBookingService, localPaymentService)
        every { localBookingService.requestBooking(1L, 42L) } throws SlotBusyException(42L)

        When("execute를 호출하면") {
            Then("[U-01] SlotBusyException이 전파되고 PaymentDomainService는 호출되지 않는다") {
                shouldThrow<SlotBusyException> {
                    localUseCase.execute(command)
                }
                verify(exactly = 0) { localPaymentService.create(any(), any(), any(), any(), any(), any(), any()) }
            }
        }
    }
})
