package com.sportsapp.application.booking

import com.sportsapp.domain.booking.Booking
import com.sportsapp.domain.booking.BookingDomainService
import com.sportsapp.domain.booking.BookingStatus
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

class ListMyBookingsUseCaseTest : BehaviorSpec({

    val bookingDomainService = mockk<BookingDomainService>()
    val listMyBookingsUseCase = ListMyBookingsUseCase(bookingDomainService)

    Given("userId=1, status=null 조건") {
        val pageable = PageRequest.of(0, 20)
        val booking1 = mockk<Booking>(relaxed = true) {
            every { id } returns 1L
            every { userId } returns 1L
            every { slotId } returns 10L
            every { status } returns BookingStatus.CONFIRMED
            every { paymentId } returns 100L
            every { createdAt } returns java.time.ZonedDateTime.now()
            every { updatedAt } returns java.time.ZonedDateTime.now()
        }
        val booking2 = mockk<Booking>(relaxed = true) {
            every { id } returns 2L
            every { userId } returns 1L
            every { slotId } returns 11L
            every { status } returns BookingStatus.PENDING
            every { paymentId } returns null
            every { createdAt } returns java.time.ZonedDateTime.now()
            every { updatedAt } returns java.time.ZonedDateTime.now()
        }
        every {
            bookingDomainService.findMyBookings(userId = 1L, status = null, pageable = pageable)
        } returns PageImpl(listOf(booking1, booking2), pageable, 2L)

        When("status 미지정으로 execute를 호출하면") {
            val command = ListBookingsCommand(userId = 1L, status = null, pageable = pageable)
            val response = listMyBookingsUseCase.execute(command)

            Then("[U-01] 전체 상태가 포함된 2건이 반환된다") {
                response.bookings.size shouldBe 2
                response.totalElements shouldBe 2L
                response.bookings[0].status shouldBe BookingStatus.CONFIRMED
                response.bookings[1].status shouldBe BookingStatus.PENDING
            }
        }
    }
})
