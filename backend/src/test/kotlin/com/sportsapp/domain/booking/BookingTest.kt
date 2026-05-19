package com.sportsapp.domain.booking

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.ZonedDateTime

class BookingTest : BehaviorSpec({

    Given("PENDING 상태의 Booking") {
        val booking = Booking.createPending(
            userId = 1L,
            slotId = 10L,
            createdAt = ZonedDateTime.now(),
        )

        Then("[U-01] status=PENDING, paymentId=null로 생성된다") {
            booking.status shouldBe BookingStatus.PENDING
            booking.paymentId.shouldBeNull()
        }

        When("confirm(paymentId)을 호출하면") {
            booking.confirm(paymentId = 999L)

            Then("[U-02] PENDING → CONFIRMED 전이 시 paymentId가 채워진다") {
                booking.status shouldBe BookingStatus.CONFIRMED
                booking.paymentId shouldBe 999L
            }
        }
    }

    Given("CANCELLED 상태의 Booking") {
        val booking = Booking.reconstruct(
            id = 1L,
            userId = 1L,
            slotId = 10L,
            status = BookingStatus.CANCELLED,
            paymentId = null,
            createdAt = ZonedDateTime.now(),
        )

        When("confirm을 호출하면") {
            Then("[U-03] InvalidBookingStateException을 던진다") {
                shouldThrow<InvalidBookingStateException> {
                    booking.confirm(paymentId = 999L)
                }
            }
        }
    }

    Given("BookingStatus 전이 규칙") {
        Then("[U-04] 9개 상태 전이 케이스가 정확히 판단된다") {
            // PENDING → CONFIRMED: 허용
            BookingStatus.PENDING.canTransitTo(BookingStatus.CONFIRMED) shouldBe true
            // PENDING → CANCELLED: 허용
            BookingStatus.PENDING.canTransitTo(BookingStatus.CANCELLED) shouldBe true
            // PENDING → EXPIRED: 허용
            BookingStatus.PENDING.canTransitTo(BookingStatus.EXPIRED) shouldBe true
            // CONFIRMED → CANCELLED: 허용
            BookingStatus.CONFIRMED.canTransitTo(BookingStatus.CANCELLED) shouldBe true
            // CONFIRMED → CONFIRMED: 금지
            BookingStatus.CONFIRMED.canTransitTo(BookingStatus.CONFIRMED) shouldBe false
            // CONFIRMED → EXPIRED: 금지
            BookingStatus.CONFIRMED.canTransitTo(BookingStatus.EXPIRED) shouldBe false
            // CANCELLED → *: 금지
            BookingStatus.CANCELLED.canTransitTo(BookingStatus.CONFIRMED) shouldBe false
            // EXPIRED → *: 금지
            BookingStatus.EXPIRED.canTransitTo(BookingStatus.CONFIRMED) shouldBe false
            // EXPIRED → CANCELLED: 금지
            BookingStatus.EXPIRED.canTransitTo(BookingStatus.CANCELLED) shouldBe false
        }
    }

    Given("잘못된 timeRange 형식") {
        Then("[U-05] InvalidSlotException을 던진다") {
            shouldThrow<InvalidSlotException> {
                Slot.create(
                    facilityId = "FAC-01",
                    date = ZonedDateTime.now(),
                    timeRange = "9:00-10:00",
                    capacity = 10,
                )
            }
        }
    }

    Given("올바른 timeRange 형식") {
        Then("[U-05] Slot이 정상 생성된다") {
            val slot = Slot.create(
                facilityId = "FAC-01",
                date = ZonedDateTime.now(),
                timeRange = "09:00-10:00",
                capacity = 10,
            )
            slot.timeRange shouldBe "09:00-10:00"
        }
    }
})
