package com.sportsapp.domain.booking.dto

import com.sportsapp.domain.booking.entity.Booking
import com.sportsapp.domain.booking.entity.BookingStatus
import com.sportsapp.domain.booking.entity.Slot
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import java.time.ZoneOffset
import java.time.ZonedDateTime

class BookingDetailTest : BehaviorSpec({

    Given("Slot이 존재하는 Booking 단건 조회 결과") {
        val expectedCreatedAt = ZonedDateTime.of(2026, 7, 8, 9, 0, 0, 0, ZoneOffset.UTC)
        val expectedUpdatedAt = ZonedDateTime.of(2026, 7, 8, 9, 30, 0, 0, ZoneOffset.UTC)
        val slotDate = ZonedDateTime.of(2026, 7, 10, 0, 0, 0, 0, ZoneOffset.UTC)

        val booking = mockk<Booking>(relaxed = true) {
            every { id } returns 10L
            every { slotId } returns 42L
            every { userId } returns 7L
            every { status } returns BookingStatus.CONFIRMED
            every { paymentId } returns 100L
            every { createdAt } returns expectedCreatedAt
            every { updatedAt } returns expectedUpdatedAt
        }
        val slot = mockk<Slot>(relaxed = true) {
            every { facilityId } returns "FAC-01"
            every { date } returns slotDate
            every { timeRange } returns "09:00-10:00"
        }

        When("BookingDetail을 구성하면") {
            val detail = BookingDetail.of(booking, slot)

            Then("facilityId가 Slot 자기 데이터로 채워진다") {
                detail.facilityId shouldBe "FAC-01"
            }

            Then("title이 자기 데이터로 구성한 서술형 라벨이다") {
                detail.title shouldBe "2026-07-10 09:00-10:00 시설 예약"
            }

            Then("title이 BOOKING #id 형태의 기술 식별자를 포함하지 않는다") {
                detail.title shouldNotContain "BOOKING"
                detail.title shouldNotContain "#"
            }

            Then("나머지 필드는 booking 자기 데이터를 그대로 보존한다") {
                detail.bookingId shouldBe 10L
                detail.slotId shouldBe 42L
                detail.userId shouldBe 7L
                detail.status shouldBe BookingStatus.CONFIRMED
                detail.paymentId shouldBe 100L
                detail.createdAt shouldBe expectedCreatedAt
                detail.updatedAt shouldBe expectedUpdatedAt
            }
        }
    }

    Given("참조 Slot이 삭제·부재라 date/timeRange를 확보할 수 없는 Booking 단건 조회 결과") {
        val expectedCreatedAt = ZonedDateTime.now()
        val expectedUpdatedAt = ZonedDateTime.now()
        val booking = mockk<Booking>(relaxed = true) {
            every { id } returns 20L
            every { slotId } returns 99L
            every { userId } returns 7L
            every { status } returns BookingStatus.PENDING
            every { paymentId } returns null
            every { createdAt } returns expectedCreatedAt
            every { updatedAt } returns expectedUpdatedAt
        }

        When("Slot 없이 BookingDetail을 구성하면") {
            val detail = BookingDetail.of(booking, slot = null)

            Then("facilityId는 원본 시설을 알 수 없으므로 null이다") {
                detail.facilityId shouldBe null
            }

            Then("title은 기본 라벨(시설 예약)로 방어 반환한다") {
                detail.title shouldBe "시설 예약"
            }
        }
    }
})
