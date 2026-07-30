package com.sportsapp.application.booking

import com.sportsapp.application.booking.usecase.GetBookingUseCase
import com.sportsapp.domain.booking.dto.BookingDetail
import com.sportsapp.domain.booking.entity.BookingStatus
import com.sportsapp.domain.booking.exception.UnauthorizedBookingAccessException
import com.sportsapp.domain.booking.service.BookingDomainService
import com.sportsapp.domain.payment.entity.PaymentStatus
import com.sportsapp.domain.payment.service.PaymentDomainService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.ZonedDateTime

class GetBookingUseCaseTest : BehaviorSpec({

    val bookingDomainService = mockk<BookingDomainService>()
    val paymentDomainService = mockk<PaymentDomainService>()
    val getBookingUseCase = GetBookingUseCase(bookingDomainService, paymentDomainService)

    Given("requesterId=1, bookingId=10인 본인 Booking 상세(paymentId=50, facilityId=FAC-01)") {
        val createdAt = ZonedDateTime.now()
        val updatedAt = ZonedDateTime.now()
        val detail = BookingDetail(
            bookingId = 10L,
            slotId = 99L,
            facilityId = "FAC-01",
            userId = 1L,
            status = BookingStatus.CONFIRMED,
            paymentId = 50L,
            title = "2026-07-10 09:00-10:00 시설 예약",
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
        every { bookingDomainService.getBookingDetail(requesterId = 1L, bookingId = 10L) } returns detail
        every { paymentDomainService.findStatuses(listOf(50L)) } returns mapOf(50L to PaymentStatus.COMPLETED)

        When("본인이 단건 조회하면") {
            val response = getBookingUseCase.execute(requesterId = 1L, bookingId = 10L)

            Then("BookingResponse가 paymentStatus=COMPLETED로 반환된다") {
                response.id shouldBe 10L
                response.userId shouldBe 1L
                response.status shouldBe BookingStatus.CONFIRMED
                response.paymentId shouldBe 50L
                response.paymentStatus shouldBe PaymentStatus.COMPLETED
            }

            Then("facilityId·title이 상세 조회 결과 그대로 채워진다") {
                response.facilityId shouldBe "FAC-01"
                response.title shouldBe "2026-07-10 09:00-10:00 시설 예약"
            }
        }
    }

    Given("requesterId=2인 타인이 bookingId=10 조회 시도") {
        every {
            bookingDomainService.getBookingDetail(requesterId = 2L, bookingId = 10L)
        } throws UnauthorizedBookingAccessException(10L)

        When("타인이 단건 조회하면") {
            Then("UnauthorizedBookingAccessException이 발생한다") {
                shouldThrow<UnauthorizedBookingAccessException> {
                    getBookingUseCase.execute(requesterId = 2L, bookingId = 10L)
                }
            }
        }
    }

    Given("paymentId=null인 PENDING Booking 상세, 참조 Slot 부재로 facilityId도 null") {
        val detail = BookingDetail(
            bookingId = 20L,
            slotId = 88L,
            facilityId = null,
            userId = 1L,
            status = BookingStatus.PENDING,
            paymentId = null,
            title = "시설 예약",
            createdAt = ZonedDateTime.now(),
            updatedAt = ZonedDateTime.now(),
        )
        every { bookingDomainService.getBookingDetail(requesterId = 1L, bookingId = 20L) } returns detail

        When("단건 조회하면") {
            val response = getBookingUseCase.execute(requesterId = 1L, bookingId = 20L)

            Then("paymentId=null이면 paymentStatus도 null로 반환된다") {
                response.paymentId shouldBe null
                response.paymentStatus shouldBe null
                response.status shouldBe BookingStatus.PENDING
            }

            Then("facilityId는 null이고 title은 기본 라벨을 유지한다") {
                response.facilityId shouldBe null
                response.title shouldBe "시설 예약"
            }
        }
    }
})
