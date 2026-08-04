package com.sportsapp.domain.booking.dto

import com.sportsapp.domain.booking.entity.BookingStatus
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import java.math.BigDecimal
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
                createdAt = createdAt,
                slotLabelSource = BookingOrderItem.SlotLabelSource(
                    date = slotDate,
                    timeRange = "09:00-10:00",
                ),
                chargeSource = BookingOrderItem.ChargeSource(paymentId = 100L, amount = null),
            )

            Then("title이 자기 데이터로 구성한 서술형 라벨(한국어 날짜 timeRange 시설 예약)이다") {
                item.title shouldBe "2026. 07. 10. 09:00-10:00 시설 예약"
            }

            Then("title이 BOOKING #id 형태의 기술 식별자를 포함하지 않는다") {
                item.title shouldNotContain "BOOKING"
                item.title shouldNotContain "#"
            }
        }
    }

    Given("결제가 연결되고 금액이 저장된 예약 조회 결과") {
        val createdAt = ZonedDateTime.of(2026, 7, 8, 9, 0, 0, 0, ZoneOffset.UTC)

        When("BookingOrderItem을 구성하면") {
            val item = BookingOrderItem.of(
                bookingId = 3L,
                slotId = 42L,
                userId = 7L,
                status = BookingStatus.CONFIRMED,
                createdAt = createdAt,
                slotLabelSource = BookingOrderItem.SlotLabelSource(
                    date = createdAt,
                    timeRange = "09:00-10:00",
                ),
                chargeSource = BookingOrderItem.ChargeSource(
                    paymentId = 100L,
                    amount = BigDecimal("35000"),
                ),
            )

            Then("결제 참조와 금액이 그대로 전달된다") {
                item.paymentId shouldBe 100L
                item.amount shouldBe BigDecimal("35000")
            }
        }
    }

    Given("금액 저장 이력이 없고(V65 이전) 결제도 연결되지 않은 예약 조회 결과") {
        val createdAt = ZonedDateTime.of(2026, 7, 8, 9, 0, 0, 0, ZoneOffset.UTC)

        When("BookingOrderItem을 구성하면") {
            val item = BookingOrderItem.of(
                bookingId = 4L,
                slotId = 42L,
                userId = 7L,
                status = BookingStatus.CONFIRMED,
                createdAt = createdAt,
                slotLabelSource = BookingOrderItem.SlotLabelSource(
                    date = createdAt,
                    timeRange = "09:00-10:00",
                ),
                chargeSource = BookingOrderItem.ChargeSource(
                    paymentId = null,
                    amount = null,
                ),
            )

            Then("결제 참조와 금액이 모두 null로 노출된다 (금액 확정 불가)") {
                item.paymentId shouldBe null
                item.amount shouldBe null
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
                createdAt = createdAt,
                slotLabelSource = BookingOrderItem.SlotLabelSource(
                    date = null,
                    timeRange = null,
                ),
                chargeSource = BookingOrderItem.ChargeSource(paymentId = null, amount = null),
            )

            Then("기본 라벨(시설 예약)로 방어 반환한다") {
                item.title shouldBe "시설 예약"
            }
        }
    }
})
