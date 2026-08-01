package com.sportsapp.application.booking

import com.sportsapp.domain.booking.dto.BookingDetail
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

    fun bookingDetail(
        bookingId: Long,
        slotId: Long,
        status: BookingStatus,
        paymentId: Long?,
        title: String,
    ) = BookingDetail(
        bookingId = bookingId,
        slotId = slotId,
        facilityId = "fac-001",
        userId = 1L,
        status = status,
        paymentId = paymentId,
        title = title,
        createdAt = java.time.ZonedDateTime.now(),
        updatedAt = java.time.ZonedDateTime.now(),
    )

    Given("userId=1, status=null 조건 — booking1은 paymentId=100, booking2는 paymentId=null") {
        val pageable = PageRequest.of(0, 20)
        val detail1 = bookingDetail(1L, 10L, BookingStatus.CONFIRMED, 100L, "2026-08-01 07:00-08:00 시설 예약")
        val detail2 = bookingDetail(2L, 11L, BookingStatus.PENDING, null, "2026-08-01 10:00-11:00 시설 예약")
        every {
            bookingDomainService.findMyBookingDetails(userId = 1L, status = null, pageable = pageable)
        } returns PageImpl(listOf(detail1, detail2), pageable, 2L)
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

            Then("[U-02] 목록 응답에 사람이 읽는 예약 라벨과 시설 식별자가 채워진다") {
                response.bookings[0].title shouldBe "2026-08-01 07:00-08:00 시설 예약"
                response.bookings[0].facilityId shouldBe "fac-001"
                response.bookings[1].title shouldBe "2026-08-01 10:00-11:00 시설 예약"
            }
        }
    }
})
