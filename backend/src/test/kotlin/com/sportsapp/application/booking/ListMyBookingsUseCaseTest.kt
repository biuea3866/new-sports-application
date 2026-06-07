package com.sportsapp.application.booking

import com.sportsapp.domain.booking.entity.Booking
import com.sportsapp.domain.booking.service.BookingDomainService
import com.sportsapp.domain.booking.entity.BookingStatus
import com.sportsapp.domain.payment.service.PaymentDomainService
import com.sportsapp.domain.payment.entity.PaymentStatus
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import com.sportsapp.application.booking.usecase.ListMyBookingsUseCase
import com.sportsapp.application.booking.dto.ListBookingsCommand

class ListMyBookingsUseCaseTest : BehaviorSpec({

    val bookingDomainService = mockk<BookingDomainService>()
    val paymentDomainService = mockk<PaymentDomainService>()
    val listMyBookingsUseCase = ListMyBookingsUseCase(bookingDomainService, paymentDomainService)

    Given("userId=1, status=null 조건 — booking1은 paymentId=100, booking2는 paymentId=null") {
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
        every { paymentDomainService.findStatuses(listOf(100L)) } returns mapOf(100L to PaymentStatus.COMPLETED)

        When("status 미지정으로 execute를 호출하면") {
            val command = ListBookingsCommand(userId = 1L, status = null, pageable = pageable)
            val response = listMyBookingsUseCase.execute(command)

            Then("[U-01] 2건이 반환되며 paymentStatus가 올바르게 매핑된다") {
                response.bookings.size shouldBe 2
                response.totalElements shouldBe 2L
                response.bookings[0].status shouldBe BookingStatus.CONFIRMED
                response.bookings[0].paymentStatus shouldBe PaymentStatus.COMPLETED
                response.bookings[1].status shouldBe BookingStatus.PENDING
                response.bookings[1].paymentStatus shouldBe null
            }
        }
    }
})
