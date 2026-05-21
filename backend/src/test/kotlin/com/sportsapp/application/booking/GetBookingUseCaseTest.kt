package com.sportsapp.application.booking

import com.sportsapp.domain.booking.Booking
import com.sportsapp.domain.booking.BookingDomainService
import com.sportsapp.domain.booking.BookingStatus
import com.sportsapp.domain.booking.UnauthorizedBookingAccessException
import com.sportsapp.domain.payment.PaymentDomainService
import com.sportsapp.domain.payment.PaymentStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

class GetBookingUseCaseTest : BehaviorSpec({

    val bookingDomainService = mockk<BookingDomainService>()
    val paymentDomainService = mockk<PaymentDomainService>()
    val getBookingUseCase = GetBookingUseCase(bookingDomainService, paymentDomainService)

    Given("requesterId=1, bookingId=10인 본인 Booking (paymentId=50)") {
        val booking = mockk<Booking>(relaxed = true) {
            every { id } returns 10L
            every { userId } returns 1L
            every { slotId } returns 99L
            every { status } returns BookingStatus.CONFIRMED
            every { paymentId } returns 50L
            every { createdAt } returns java.time.ZonedDateTime.now()
            every { updatedAt } returns java.time.ZonedDateTime.now()
        }
        every { bookingDomainService.getBooking(requesterId = 1L, bookingId = 10L) } returns booking
        every { paymentDomainService.findStatuses(listOf(50L)) } returns mapOf(50L to PaymentStatus.COMPLETED)

        When("본인이 단건 조회하면") {
            val response = getBookingUseCase.execute(requesterId = 1L, bookingId = 10L)

            Then("[U-02] BookingResponse가 paymentStatus=COMPLETED로 반환된다") {
                response.id shouldBe 10L
                response.userId shouldBe 1L
                response.status shouldBe BookingStatus.CONFIRMED
                response.paymentId shouldBe 50L
                response.paymentStatus shouldBe PaymentStatus.COMPLETED
            }
        }
    }

    Given("requesterId=2인 타인이 bookingId=10 조회 시도") {
        every {
            bookingDomainService.getBooking(requesterId = 2L, bookingId = 10L)
        } throws UnauthorizedBookingAccessException(10L)

        When("타인이 단건 조회하면") {
            Then("[U-02] UnauthorizedBookingAccessException이 발생한다") {
                shouldThrow<UnauthorizedBookingAccessException> {
                    getBookingUseCase.execute(requesterId = 2L, bookingId = 10L)
                }
            }
        }
    }

    Given("paymentId=null인 PENDING Booking") {
        val booking = mockk<Booking>(relaxed = true) {
            every { id } returns 20L
            every { userId } returns 1L
            every { slotId } returns 88L
            every { status } returns BookingStatus.PENDING
            every { paymentId } returns null
            every { createdAt } returns java.time.ZonedDateTime.now()
            every { updatedAt } returns java.time.ZonedDateTime.now()
        }
        every { bookingDomainService.getBooking(requesterId = 1L, bookingId = 20L) } returns booking

        When("단건 조회하면") {
            val response = getBookingUseCase.execute(requesterId = 1L, bookingId = 20L)

            Then("[U-02] paymentId=null이면 paymentStatus도 null로 반환된다") {
                response.paymentId shouldBe null
                response.paymentStatus shouldBe null
                response.status shouldBe BookingStatus.PENDING
            }
        }
    }
})
