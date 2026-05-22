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
        val booking = Booking.createPending(userId = 1L, slotId = 10L)
        booking.cancel()

        When("confirm을 호출하면") {
            Then("[U-03] InvalidBookingStateException을 던진다") {
                shouldThrow<InvalidBookingStateException> {
                    booking.confirm(paymentId = 999L)
                }
            }
        }
    }

    Given("BookingStatus 전이 규칙") {
        Then("[U-04] 상태 전이 케이스가 정확히 판단된다") {
            // PENDING → CONFIRMED: 허용
            BookingStatus.PENDING.canTransitTo(BookingStatus.CONFIRMED) shouldBe true
            // PENDING → CANCELLED: 허용
            BookingStatus.PENDING.canTransitTo(BookingStatus.CANCELLED) shouldBe true
            // PENDING → EXPIRED: 허용
            BookingStatus.PENDING.canTransitTo(BookingStatus.EXPIRED) shouldBe true
            // CONFIRMED → CANCELLED: 허용
            BookingStatus.CONFIRMED.canTransitTo(BookingStatus.CANCELLED) shouldBe true
            // CONFIRMED → REFUNDED: 허용 (Phase 2)
            BookingStatus.CONFIRMED.canTransitTo(BookingStatus.REFUNDED) shouldBe true
            // CONFIRMED → CONFIRMED: 금지
            BookingStatus.CONFIRMED.canTransitTo(BookingStatus.CONFIRMED) shouldBe false
            // CONFIRMED → EXPIRED: 금지
            BookingStatus.CONFIRMED.canTransitTo(BookingStatus.EXPIRED) shouldBe false
            // CANCELLED → *: 금지
            BookingStatus.CANCELLED.canTransitTo(BookingStatus.CONFIRMED) shouldBe false
            // EXPIRED → *: 금지
            BookingStatus.EXPIRED.canTransitTo(BookingStatus.CONFIRMED) shouldBe false
            // REFUNDED → *: 금지
            BookingStatus.REFUNDED.canTransitTo(BookingStatus.CANCELLED) shouldBe false
        }
    }

    Given("CONFIRMED 상태의 Booking (paymentId 있음)") {
        val booking = Booking.createPending(userId = 1L, slotId = 10L)
        booking.confirm(paymentId = 10L)

        When("refund() 를 호출하면") {
            Then("[U-refund-01] CONFIRMED → REFUNDED 전이가 성공한다") {
                booking.refund()
                booking.status shouldBe BookingStatus.REFUNDED
            }
        }
    }

    Given("CANCELLED 상태의 Booking") {
        val cancelledBooking = Booking.createPending(userId = 1L, slotId = 10L)
        cancelledBooking.cancel()

        When("refund() 를 호출하면") {
            Then("[U-refund-02] RefundPolicyViolationException 이 발생한다") {
                shouldThrow<RefundPolicyViolationException> {
                    cancelledBooking.refund()
                }
            }
        }
    }

    Given("paymentId 가 없는 PENDING 상태의 Booking") {
        val pendingBooking = Booking.createPending(userId = 1L, slotId = 10L)

        When("requireHasPayment() 를 호출하면") {
            Then("[U-refund-03] RefundBookingException 이 발생한다") {
                shouldThrow<RefundBookingException> {
                    pendingBooking.requireHasPayment()
                }
            }
        }
    }

    Given("CONFIRMED 상태의 Booking (userId=1L)") {
        val booking = Booking.createPending(userId = 1L, slotId = 10L)
        booking.confirm(paymentId = 10L)

        When("소유자가 아닌 userId=99L 로 requireOwnedBy 를 호출하면") {
            Then("[U-refund-04] UnauthorizedBookingAccessException 이 발생한다") {
                shouldThrow<UnauthorizedBookingAccessException> {
                    booking.requireOwnedBy(99L)
                }
            }
        }

        When("소유자 userId=1L 로 requireOwnedBy 를 호출하면") {
            Then("[U-refund-05] 예외 없이 통과한다") {
                booking.requireOwnedBy(1L) // 예외 없음
            }
        }
    }

    Given("이미 REFUNDED 상태의 Booking") {
        val booking = Booking.createPending(userId = 1L, slotId = 10L)
        booking.confirm(paymentId = 10L)
        booking.refund()

        When("refund() 를 다시 호출하면") {
            Then("[U-refund-06] RefundPolicyViolationException 이 발생한다") {
                shouldThrow<RefundPolicyViolationException> {
                    booking.refund()
                }
            }
        }
    }

    Given("PENDING 상태의 Booking") {
        val booking = Booking.createPending(userId = 1L, slotId = 10L)

        When("refund() 를 호출하면") {
            Then("[U-refund-07] PENDING → REFUNDED 전이 불가로 RefundPolicyViolationException 이 발생한다") {
                shouldThrow<RefundPolicyViolationException> {
                    booking.refund()
                }
            }
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
                    ownerId = 1L,
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
                ownerId = 1L,
            )
            slot.timeRange shouldBe "09:00-10:00"
        }
    }
})
