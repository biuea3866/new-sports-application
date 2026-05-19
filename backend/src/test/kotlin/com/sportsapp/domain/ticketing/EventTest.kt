package com.sportsapp.domain.ticketing

import com.sportsapp.domain.ticketing.exception.InvalidEventStateException
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
    )

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
})
