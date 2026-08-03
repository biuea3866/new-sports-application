package com.sportsapp.domain.ticketing.dto

import com.sportsapp.domain.ticketing.entity.Event
import com.sportsapp.domain.ticketing.entity.EventStatus
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.ZoneOffset
import java.time.ZonedDateTime

class EventWithSeatCountsTest : BehaviorSpec({

    fun buildEvent() = Event(
        id = 1L,
        title = "2026 시티리그 4강 홈경기",
        venue = "잠실 실내체육관",
        startsAt = ZonedDateTime.of(2026, 8, 10, 19, 0, 0, 0, ZoneOffset.UTC),
        status = EventStatus.OPEN,
        ownerId = 7L,
    )

    Given("총 90석 중 2석이 팔린 경기") {
        val eventWithSeatCounts = EventWithSeatCounts(event = buildEvent(), totalSeats = 90L, soldSeats = 2L)

        When("잔여 좌석을 조회하면") {
            Then("총 좌석에서 판매 좌석을 뺀 값이 나온다") {
                eventWithSeatCounts.availableSeats shouldBe 88L
            }
        }
    }

    Given("좌석이 등록되지 않은 경기") {
        val eventWithSeatCounts = EventWithSeatCounts(event = buildEvent(), totalSeats = 0L, soldSeats = 0L)

        When("잔여 좌석을 조회하면") {
            Then("0이 나온다") {
                eventWithSeatCounts.availableSeats shouldBe 0L
            }
        }
    }

    Given("판매 좌석이 총 좌석보다 많은 비정상 데이터") {
        // 좌석 soft delete 이후 발권 티켓이 남는 등으로 역전될 수 있다. 화면에 음수 잔여석이
        // 노출되지 않도록 0에서 막는다.
        val eventWithSeatCounts = EventWithSeatCounts(event = buildEvent(), totalSeats = 5L, soldSeats = 8L)

        When("잔여 좌석을 조회하면") {
            Then("음수가 아니라 0으로 막힌다") {
                eventWithSeatCounts.availableSeats shouldBe 0L
            }
        }
    }
})
