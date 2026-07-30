package com.sportsapp.domain.booking

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import com.sportsapp.domain.booking.entity.Booking
import com.sportsapp.domain.booking.entity.BookingStatus
import com.sportsapp.domain.booking.event.BookingCancelledEvent
import com.sportsapp.domain.booking.exception.InvalidBookingStateException
import com.sportsapp.domain.booking.exception.UnauthorizedBookingAccessException

class BookingCancelTest : BehaviorSpec({

    Given("CONFIRMED 상태의 Booking") {
        val booking = Booking.createPending(userId = 1L, slotId = 10L)
        booking.confirm(paymentId = 100L)
        booking.pullDomainEvents() // BookingEvent.Confirmed를 소비하여 cancel 이벤트만 검증

        When("소유자가 취소를 요청하면") {
            booking.cancel(cancelledByUserId = 1L, reason = "일정 변경")

            Then("[U-01] CONFIRMED → CANCELLED 전이가 성공하고 이벤트가 적재된다") {
                booking.status shouldBe BookingStatus.CANCELLED
                val events = booking.pullDomainEvents()
                events.size shouldBe 1
                val event = events[0] as BookingCancelledEvent
                event.aggregateId shouldBe booking.id
                event.cancelledByUserId shouldBe 1L
                event.reason shouldBe "일정 변경"
            }
        }
    }

    Given("이미 CANCELLED 상태의 Booking") {
        val booking = Booking.createPending(userId = 1L, slotId = 10L)
        booking.cancel()

        When("다시 cancel을 호출하면") {
            Then("[U-01] InvalidBookingStateException이 발생한다") {
                shouldThrow<InvalidBookingStateException> {
                    booking.cancel(cancelledByUserId = 1L, reason = null)
                }
            }
        }
    }

    Given("EXPIRED 상태의 Booking") {
        val booking = Booking.createPending(userId = 1L, slotId = 10L)
        booking.expire()

        When("cancel을 호출하면") {
            Then("[U-02] EXPIRED 상태에서는 InvalidBookingStateException이 발생한다") {
                shouldThrow<InvalidBookingStateException> {
                    booking.cancel(cancelledByUserId = 1L, reason = null)
                }
            }
        }
    }

    Given("userId=1L 소유자의 PENDING Booking") {
        val booking = Booking.createPending(userId = 1L, slotId = 10L)

        When("다른 사용자(userId=99L)가 cancel을 시도하면") {
            Then("[U-04] UnauthorizedBookingAccessException이 발생한다") {
                shouldThrow<UnauthorizedBookingAccessException> {
                    booking.cancel(cancelledByUserId = 99L, reason = null)
                }
            }
        }
    }

    Given("PENDING 상태의 Booking") {
        val booking = Booking.createPending(userId = 1L, slotId = 10L)

        When("소유자가 reason 없이 cancel을 요청하면") {
            booking.cancel(cancelledByUserId = 1L, reason = null)

            Then("[U-03] reason=null이어도 정상 취소된다") {
                booking.status shouldBe BookingStatus.CANCELLED
            }
        }
    }
})
