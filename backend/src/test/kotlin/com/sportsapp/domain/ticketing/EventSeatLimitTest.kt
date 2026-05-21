package com.sportsapp.domain.ticketing

import com.sportsapp.domain.ticketing.exception.InvalidEventStateException
import com.sportsapp.domain.ticketing.exception.TooManySeatsException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.ZoneOffset
import java.time.ZonedDateTime

class EventSeatLimitTest : BehaviorSpec({

    val startsAt = ZonedDateTime.of(2026, 12, 1, 18, 0, 0, 0, ZoneOffset.UTC)

    fun buildSeatSpec(index: Int) = SeatSpec(
        section = "A",
        rowNo = ((index - 1) / 10 + 1).toString(),
        seatNo = ((index - 1) % 10 + 1).toString(),
        price = BigDecimal("50000"),
    )

    Given("좌석 수가 500개인 SeatSpec 리스트") {
        val seatSpecs = (1..500).map { buildSeatSpec(it) }

        When("[U-01] Event.validateSeatLimit(seatSpecs)를 호출하면") {
            Then("예외가 발생하지 않는다") {
                Event.validateSeatLimit(seatSpecs)
            }
        }
    }

    Given("좌석 수가 501개인 SeatSpec 리스트") {
        val seatSpecs = (1..501).map { buildSeatSpec(it) }

        When("[U-01] Event.validateSeatLimit(seatSpecs)를 호출하면") {
            Then("TooManySeatsException이 발생한다") {
                shouldThrow<TooManySeatsException> {
                    Event.validateSeatLimit(seatSpecs)
                }
            }
        }
    }

    Given("SCHEDULED 상태의 Event") {
        val event = Event(
            id = 1L,
            title = "Test Event",
            venue = "Test Venue",
            startsAt = startsAt,
            status = EventStatus.SCHEDULED,
            ownerId = 1L,
        )

        When("[U-02] close()를 직접 호출하면") {
            Then("InvalidEventStateException이 발생한다 (SCHEDULED → CLOSED 불허)") {
                shouldThrow<InvalidEventStateException> {
                    event.close()
                }
            }
        }
    }

    Given("OPEN 상태의 Event") {
        val event = Event(
            id = 2L,
            title = "Open Event",
            venue = "Test Venue",
            startsAt = startsAt,
            status = EventStatus.OPEN,
            ownerId = 1L,
        )

        When("[U-02] close()를 호출하면") {
            event.close()

            Then("상태가 CLOSED로 전이된다") {
                event.status shouldBe EventStatus.CLOSED
            }
        }
    }

    Given("CLOSED 상태의 Event") {
        val event = Event(
            id = 3L,
            title = "Closed Event",
            venue = "Test Venue",
            startsAt = startsAt,
            status = EventStatus.CLOSED,
            ownerId = 1L,
        )

        When("[U-02] close()를 다시 호출하면") {
            Then("InvalidEventStateException이 발생한다") {
                shouldThrow<InvalidEventStateException> {
                    event.close()
                }
            }
        }
    }
})
