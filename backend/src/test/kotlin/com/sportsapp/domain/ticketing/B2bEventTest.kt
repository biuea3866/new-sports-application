package com.sportsapp.domain.ticketing

import com.sportsapp.domain.ticketing.exception.InvalidEventStateException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.ZonedDateTime

class B2bEventTest : BehaviorSpec({

    Given("SCHEDULED 상태의 Event가 있을 때") {
        val startsAt = ZonedDateTime.now().plusDays(30)
        val event = Event.create("Concert", "Seoul Arena", startsAt, 1L)

        When("update를 호출하면") {
            val newStartsAt = startsAt.plusDays(5)
            event.update("New Concert", "Busan Arena", newStartsAt)

            Then("[U-01] title/venue/startsAt이 갱신된다") {
                event.title shouldBe "New Concert"
                event.venue shouldBe "Busan Arena"
                event.startsAt shouldBe newStartsAt
                event.status shouldBe EventStatus.SCHEDULED
            }
        }
    }

    Given("OPEN 상태의 Event가 있을 때") {
        val event = Event.create("Concert", "Seoul Arena", ZonedDateTime.now().plusDays(30), 1L)
        event.openSales()

        When("update를 호출하면") {
            Then("[U-02] SCHEDULED 상태가 아니면 InvalidEventStateException이 발생한다") {
                shouldThrow<InvalidEventStateException> {
                    event.update("New Title", "New Venue", ZonedDateTime.now().plusDays(40))
                }
            }
        }
    }

    Given("OPEN 상태의 Event가 있을 때") {
        val event = Event.create("Concert", "Seoul Arena", ZonedDateTime.now().plusDays(30), 1L)
        event.openSales()

        When("close를 호출하면") {
            event.close()

            Then("[U-03] CLOSED 상태로 전이된다") {
                event.status shouldBe EventStatus.CLOSED
            }
        }
    }

    Given("SCHEDULED 상태의 Event가 있을 때") {
        val event = Event.create("Concert", "Seoul Arena", ZonedDateTime.now().plusDays(30), 1L)

        When("close를 직접 호출하면") {
            Then("[U-04] SCHEDULED → CLOSED 전이는 InvalidEventStateException이 발생한다") {
                shouldThrow<InvalidEventStateException> {
                    event.close()
                }
            }
        }
    }

    Given("CLOSED 상태의 Event가 있을 때") {
        val event = Event.create("Concert", "Seoul Arena", ZonedDateTime.now().plusDays(30), 1L)
        event.openSales()
        event.close()

        When("update를 호출하면") {
            Then("[U-05] InvalidEventStateException이 발생한다") {
                shouldThrow<InvalidEventStateException> {
                    event.update("New Title", "New Venue", ZonedDateTime.now().plusDays(40))
                }
            }
        }
    }
})
