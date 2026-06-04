package com.sportsapp.application.booking

import com.sportsapp.domain.booking.BookingDomainService
import com.sportsapp.domain.booking.BookingResult
import com.sportsapp.domain.booking.BookingStatus
import com.sportsapp.domain.booking.SlotBusyException
import com.sportsapp.domain.payment.OrderType
import com.sportsapp.domain.payment.PaymentDomainService
import com.sportsapp.domain.payment.PaymentMethod
import com.sportsapp.domain.payment.PaymentStatus
import com.sportsapp.domain.payment.PgInitiateResult
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import org.springframework.transaction.support.TransactionTemplate

class CreateBookingUseCaseTest : BehaviorSpec({

    val command = CreateBookingCommand(
        userId = 1L,
        slotId = 42L,
        paymentMethod = PaymentMethod.CREDIT_CARD,
        amount = BigDecimal("50000"),
        currency = "KRW",
    )

    // TransactionTemplate mock that executes the callback immediately (no real tx)
    fun passthroughTransactionTemplate(): TransactionTemplate {
        val tt = mockk<TransactionTemplate>()
        every { tt.execute<Any>(any()) } answers {
            val callback = firstArg<org.springframework.transaction.support.TransactionCallback<Any>>()
            callback.doInTransaction(mockk(relaxed = true))
        }
        return tt
    }

    Given("BookingDomainService 만 호출하는 정상 흐름") {
        val bookingDomainService = mockk<BookingDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val useCase = CreateBookingUseCase(bookingDomainService, paymentDomainService, passthroughTransactionTemplate())

        val bookingResult = BookingResult(
            bookingId = 10L,
            slotId = 42L,
            userId = 1L,
            status = BookingStatus.PENDING,
        )
        val pgResult = PgInitiateResult(
            paymentId = 99L,
            status = PaymentStatus.READY,
            pgTransactionId = "tid-booking-001",
            checkoutUrl = "http://checkout",
        )
        every { bookingDomainService.requestBooking(1L, 42L) } returns bookingResult
        every {
            paymentDomainService.createPending(
                userId = 1L,
                idempotencyKey = any(),
                orderType = OrderType.BOOKING,
                orderId = 10L,
                method = PaymentMethod.CREDIT_CARD,
                amount = BigDecimal("50000"),
                currency = "KRW",
            )
        } returns 99L
        every { paymentDomainService.initiatePg(any()) } returns pgResult

        When("execute 를 호출하면") {
            val result = useCase.execute(command)

            Then("CreateBookingResult 에 bookingId 와 paymentId 가 담긴다") {
                result.bookingId shouldBe 10L
                result.paymentId shouldBe 99L
                result.status shouldBe BookingStatus.PENDING
            }

            Then("PG 호출(initiatePg)은 requestBooking + createPending tx 이후에 호출된다") {
                verify(exactly = 1) { bookingDomainService.requestBooking(1L, 42L) }
                verify(exactly = 1) { paymentDomainService.createPending(any(), any(), any(), any(), any(), any(), any()) }
                verify(exactly = 1) { paymentDomainService.initiatePg(any()) }
            }
        }
    }

    Given("락 획득 실패 흐름") {
        val bookingDomainService = mockk<BookingDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val useCase = CreateBookingUseCase(bookingDomainService, paymentDomainService, passthroughTransactionTemplate())
        every { bookingDomainService.requestBooking(1L, 42L) } throws SlotBusyException(42L)

        When("execute 를 호출하면") {
            Then("SlotBusyException 이 전파되고 PaymentDomainService 는 호출되지 않는다") {
                shouldThrow<SlotBusyException> {
                    useCase.execute(command)
                }
                verify(exactly = 0) { paymentDomainService.createPending(any(), any(), any(), any(), any(), any(), any()) }
                verify(exactly = 0) { paymentDomainService.initiatePg(any()) }
            }
        }
    }
})
