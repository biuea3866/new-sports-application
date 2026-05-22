package com.sportsapp.application.booking

import com.sportsapp.domain.booking.Booking
import com.sportsapp.domain.booking.BookingDomainService
import com.sportsapp.domain.booking.BookingStatus
import com.sportsapp.domain.booking.RefundBookingException
import com.sportsapp.domain.booking.RefundPolicyViolationException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.ZonedDateTime

class RefundBookingUseCaseTest : BehaviorSpec({

    val bookingDomainService = mockk<BookingDomainService>()
    val useCase = RefundBookingUseCase(bookingDomainService)

    Given("[U-01] 정상 환불 가능한 CONFIRMED 예약") {
        val command = RefundBookingCommand(
            bookingId = 1L,
            refundAmount = BigDecimal("50000"),
            reason = "고객 요청",
        )
        val booking = mockk<Booking> {
            every { id } returns 1L
            every { slotId } returns 10L
            every { userId } returns 42L
            every { status } returns BookingStatus.REFUNDED
            every { paymentId } returns 10L
            every { createdAt } returns ZonedDateTime.now()
            every { updatedAt } returns ZonedDateTime.now()
        }
        every {
            bookingDomainService.refundBooking(1L, BigDecimal("50000"), "고객 요청")
        } returns booking

        When("execute 를 호출하면") {
            val result = useCase.execute(command)

            Then("[U-01] BookingResponse 가 반환되고 status 가 REFUNDED 이다") {
                result.id shouldBe 1L
                result.status shouldBe BookingStatus.REFUNDED
                verify(exactly = 1) { bookingDomainService.refundBooking(1L, BigDecimal("50000"), "고객 요청") }
            }
        }
    }

    Given("[U-02] 이미 취소된 예약에 환불 시도 시") {
        val command = RefundBookingCommand(
            bookingId = 2L,
            refundAmount = BigDecimal("30000"),
            reason = "중복 요청",
        )
        every {
            bookingDomainService.refundBooking(2L, BigDecimal("30000"), "중복 요청")
        } throws RefundPolicyViolationException(2L, BookingStatus.CANCELLED)

        When("execute 를 호출하면") {
            Then("[U-02] RefundPolicyViolationException 이 전파된다") {
                shouldThrow<RefundPolicyViolationException> {
                    useCase.execute(command)
                }
            }
        }
    }

    Given("[U-03] 결제 정보가 없는 예약에 환불 시도 시") {
        val command = RefundBookingCommand(
            bookingId = 3L,
            refundAmount = BigDecimal("10000"),
            reason = "테스트",
        )
        every {
            bookingDomainService.refundBooking(3L, BigDecimal("10000"), "테스트")
        } throws RefundBookingException(3L, "결제 정보가 없는 예약은 환불할 수 없습니다.")

        When("execute 를 호출하면") {
            Then("[U-03] RefundBookingException 이 전파된다") {
                shouldThrow<RefundBookingException> {
                    useCase.execute(command)
                }
            }
        }
    }
})
