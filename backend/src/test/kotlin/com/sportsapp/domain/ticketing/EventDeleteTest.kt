package com.sportsapp.domain.ticketing

import com.sportsapp.domain.ticketing.exception.InvalidEventStateException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldNotBe
import java.time.ZoneOffset
import java.time.ZonedDateTime

class EventDeleteTest : BehaviorSpec({

    fun buildEvent(status: EventStatus) = Event(
        id = 1L,
        title = "Delete Test Event",
        venue = "Stadium",
        startsAt = ZonedDateTime.of(2027, 6, 1, 18, 0, 0, 0, ZoneOffset.UTC),
        status = status,
        ownerId = 42L,
    )

    Given("SCHEDULED 상태의 Event (티켓 미발행)") {
        val event = buildEvent(EventStatus.SCHEDULED)

        When("requireDeletable()을 호출하면") {
            Then("[U-01] 예외 없이 통과한다") {
                event.requireDeletable()
            }
        }

        When("softDelete(userId)를 호출하면") {
            event.requireDeletable()
            event.softDelete(42L)
            Then("[U-02] deletedAt이 채워진다") {
                event.deletedAt shouldNotBe null
            }
        }
    }

    Given("OPEN 상태의 Event (판매 중 — 티켓 발행됨으로 간주)") {
        val event = buildEvent(EventStatus.OPEN)

        When("requireDeletable()을 호출하면") {
            Then("[U-03] InvalidEventStateException을 던진다") {
                shouldThrow<InvalidEventStateException> { event.requireDeletable() }
            }
        }
    }

    Given("CLOSED 상태의 Event") {
        val event = buildEvent(EventStatus.CLOSED)

        When("requireDeletable()을 호출하면") {
            Then("[U-04] InvalidEventStateException을 던진다") {
                shouldThrow<InvalidEventStateException> { event.requireDeletable() }
            }
        }
    }

    Given("이미 soft-deleted된 Event") {
        val event = buildEvent(EventStatus.SCHEDULED)
        event.softDelete(1L)

        When("requireDeletable()을 호출하면") {
            Then("[U-05] IllegalStateException을 던진다 (이미 삭제됨)") {
                shouldThrow<IllegalStateException> { event.requireDeletable() }
            }
        }
    }
})
