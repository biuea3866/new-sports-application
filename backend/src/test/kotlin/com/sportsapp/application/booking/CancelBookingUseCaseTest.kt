package com.sportsapp.application.booking

import com.sportsapp.domain.booking.Booking
import com.sportsapp.domain.booking.BookingDomainService
import com.sportsapp.domain.booking.BookingStatus
import com.sportsapp.domain.booking.UnauthorizedBookingAccessException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class CancelBookingUseCaseTest : BehaviorSpec({

    val bookingDomainService = mockk<BookingDomainService>()
    val useCase = CancelBookingUseCase(bookingDomainService)

    Given("[U-03] DomainService 모킹 후 execute 성공") {
        val command = CancelBookingCommand(bookingId = 1L, cancelledByUserId = 42L, reason = "test")
        val booking = mockk<Booking> {
            every { id } returns 1L
            every { slotId } returns 10L
            every { userId } returns 42L
            every { status } returns BookingStatus.CANCELLED
            every { paymentId } returns null
            every { createdAt } returns java.time.ZonedDateTime.now()
            every { updatedAt } returns java.time.ZonedDateTime.now()
        }
        every { bookingDomainService.cancel(1L, 42L, "test") } returns booking

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("[U-03] BookingResponse가 반환되고 DomainService만 호출된다") {
                result.id shouldBe 1L
                result.status shouldBe BookingStatus.CANCELLED
                verify(exactly = 1) { bookingDomainService.cancel(1L, 42L, "test") }
            }
        }
    }

    Given("[U-04] 다른 운영자의 booking 취소 시도") {
        val command = CancelBookingCommand(bookingId = 1L, cancelledByUserId = 99L, reason = null)
        every { bookingDomainService.cancel(1L, 99L, null) } throws UnauthorizedBookingAccessException(1L)

        When("execute를 호출하면") {
            Then("[U-04] UnauthorizedBookingAccessException이 전파된다") {
                shouldThrow<UnauthorizedBookingAccessException> {
                    useCase.execute(command)
                }
            }
        }
    }
})
