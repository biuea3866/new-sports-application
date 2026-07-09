package com.sportsapp.presentation.booking.dto.response

import com.sportsapp.application.booking.dto.GetBookingResult
import com.sportsapp.domain.booking.entity.Booking
import com.sportsapp.domain.booking.entity.BookingStatus
import com.sportsapp.domain.payment.entity.PaymentStatus
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.ZonedDateTime

class BookingResponseTest : BehaviorSpec({

    Given("facilityId·title이 채워진 GetBookingResult(단건 상세 조회 결과)") {
        val createdAt = ZonedDateTime.now()
        val updatedAt = ZonedDateTime.now()
        val result = GetBookingResult(
            id = 10L,
            slotId = 42L,
            facilityId = "FAC-01",
            userId = 1L,
            status = BookingStatus.CONFIRMED,
            paymentId = 50L,
            paymentStatus = PaymentStatus.COMPLETED,
            title = "2026-07-10 09:00-10:00 시설 예약",
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

        When("BookingResponse로 변환하면") {
            val response = BookingResponse.of(result)

            Then("facilityId·title이 그대로 보존된다") {
                response.facilityId shouldBe "FAC-01"
                response.title shouldBe "2026-07-10 09:00-10:00 시설 예약"
            }

            Then("나머지 필드도 그대로 매핑된다") {
                response.id shouldBe 10L
                response.slotId shouldBe 42L
                response.userId shouldBe 1L
                response.status shouldBe BookingStatus.CONFIRMED
                response.paymentId shouldBe 50L
                response.paymentStatus shouldBe PaymentStatus.COMPLETED
            }
        }
    }

    Given("Slot 조인 없는 Booking 엔티티(취소 응답 경로)") {
        val expectedCreatedAt = ZonedDateTime.now()
        val expectedUpdatedAt = ZonedDateTime.now()
        val booking = mockk<Booking>(relaxed = true) {
            every { id } returns 20L
            every { slotId } returns 88L
            every { userId } returns 1L
            every { status } returns BookingStatus.CANCELLED
            every { paymentId } returns null
            every { createdAt } returns expectedCreatedAt
            every { updatedAt } returns expectedUpdatedAt
        }

        When("BookingResponse로 변환하면") {
            val response = BookingResponse.of(booking)

            Then("facilityId·title은 Slot 조인이 없으므로 null이다") {
                response.facilityId shouldBe null
                response.title shouldBe null
            }
        }
    }
})
