package com.sportsapp.domain.ticketing

import com.sportsapp.domain.ticketing.exception.InvalidEventStateException
import com.sportsapp.domain.ticketing.exception.TooManySeatsException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.ZonedDateTime

class EventTest : BehaviorSpec({

    fun buildEvent(status: EventStatus) = Event(
        id = 1L,
        title = "Test Event",
        venue = "Olympic Stadium",
        startsAt = ZonedDateTime.now().plusDays(7),
        status = status,
        ownerId = 1L,
    )

    Given("Event.create 팩토리를 호출할 때") {
        When("모든 인자를 전달하면") {
            val event = Event.create(
                title = "B2B Event",
                venue = "Test Stadium",
                startsAt = ZonedDateTime.now().plusDays(30),
                ownerId = 42L,
            )
            Then("[U-01] ownerId가 설정된다") {
                event.ownerId shouldBe 42L
                event.status shouldBe EventStatus.SCHEDULED
            }
        }
    }

    Given("SCHEDULED 상태의 Event") {
        val event = buildEvent(EventStatus.SCHEDULED)

        When("openSales()를 호출하면") {
            event.openSales()
            Then("[U-01] 상태가 OPEN으로 전이된다") {
                event.status shouldBe EventStatus.OPEN
            }
        }
    }

    Given("CLOSED 상태의 Event") {
        val event = buildEvent(EventStatus.CLOSED)

        When("openSales()를 호출하면") {
            Then("[U-01b] InvalidEventStateException을 던진다") {
                shouldThrow<InvalidEventStateException> { event.openSales() }
            }
        }
    }

    Given("OPEN 상태의 Event") {
        val event = buildEvent(EventStatus.OPEN)

        When("close()를 호출하면") {
            event.close()
            Then("[U-02] 상태가 CLOSED로 전이된다") {
                event.status shouldBe EventStatus.CLOSED
            }
        }
    }

    Given("SCHEDULED 상태의 Event에 close()를 호출할 때") {
        val event = buildEvent(EventStatus.SCHEDULED)

        When("close()를 호출하면") {
            Then("[U-02b] InvalidEventStateException을 던진다") {
                shouldThrow<InvalidEventStateException> { event.close() }
            }
        }
    }

    Given("좌석 스펙 리스트 500개") {
        val seats = List(500) { "SEAT-$it" }

        When("validateSeatLimit을 호출하면") {
            Then("[U-01] 예외 없이 통과한다") {
                Event.validateSeatLimit(seats)
            }
        }
    }

    Given("좌석 스펙 리스트 501개") {
        val seats = List(501) { "SEAT-$it" }

        When("validateSeatLimit을 호출하면") {
            Then("[U-01] TooManySeatsException을 던진다") {
                shouldThrow<TooManySeatsException> { Event.validateSeatLimit(seats) }
            }
        }
    }

    Given("좌석 스펙 리스트 0개") {
        val seats = emptyList<String>()

        When("validateSeatLimit을 호출하면") {
            Then("[U-02] 빈 리스트는 허용된다 (0 < 500 조건 통과)") {
                Event.validateSeatLimit(seats)
            }
        }
    }
})
