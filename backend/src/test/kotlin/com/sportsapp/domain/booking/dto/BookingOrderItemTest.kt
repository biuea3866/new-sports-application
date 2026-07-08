package com.sportsapp.domain.booking.dto

import com.sportsapp.domain.booking.entity.BookingStatus
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import java.time.ZoneOffset
import java.time.ZonedDateTime

class BookingOrderItemTest : BehaviorSpec({

    Given("Slot 정보(date, timeRange)가 있는 예약 조회 결과") {
        val createdAt = ZonedDateTime.of(2026, 7, 8, 9, 0, 0, 0, ZoneOffset.UTC)
        val slotDate = ZonedDateTime.of(2026, 7, 10, 0, 0, 0, 0, ZoneOffset.UTC)

        When("BookingOrderItem을 구성하면") {
            val item = BookingOrderItem.of(
                bookingId = 1L,
                slotId = 42L,
                userId = 7L,
                status = BookingStatus.CONFIRMED,
                paymentId = 100L,
                createdAt = createdAt,
                slotDate = slotDate,
                slotTimeRange = "09:00-10:00",
            )

            Then("title이 자기 데이터로 구성한 서술형 라벨(date timeRange 시설 예약)이다") {
                item.title shouldBe "2026-07-10 09:00-10:00 시설 예약"
            }

            Then("title이 BOOKING #id 형태의 기술 식별자를 포함하지 않는다") {
                item.title shouldNotContain "BOOKING"
                item.title shouldNotContain "#"
            }
        }
    }

    Given("참조 Slot이 삭제·부재라 date/timeRange를 확보할 수 없는 예약 조회 결과") {
        val createdAt = ZonedDateTime.now()

        When("BookingOrderItem을 구성하면") {
            val item = BookingOrderItem.of(
                bookingId = 2L,
                slotId = 99L,
                userId = 7L,
                status = BookingStatus.CONFIRMED,
                paymentId = null,
                createdAt = createdAt,
                slotDate = null,
                slotTimeRange = null,
            )

            Then("기본 라벨(시설 예약)로 방어 반환한다") {
                item.title shouldBe "시설 예약"
            }
        }
    }
})
