package com.sportsapp.domain.facility.vo

import com.sportsapp.domain.facility.exception.InvalidFacilityException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.LocalTime

class TimeRangeTest : BehaviorSpec({

    Given("start가 end보다 늦은 값이 주어지면") {
        When("TimeRange를 생성하면") {
            Then("InvalidFacilityException을 던진다") {
                shouldThrow<InvalidFacilityException> {
                    TimeRange(start = LocalTime.of(13, 0), end = LocalTime.of(12, 0))
                }
            }
        }
    }

    Given("두 시간대가 정확히 겹치는 경우") {
        val range = TimeRange(start = LocalTime.of(12, 0), end = LocalTime.of(13, 0))
        val other = TimeRange(start = LocalTime.of(12, 0), end = LocalTime.of(13, 0))

        When("overlaps를 호출하면") {
            Then("true를 반환한다") {
                range.overlaps(other) shouldBe true
            }
        }
    }

    Given("두 시간대가 경계에서만 맞닿는 경우") {
        val range = TimeRange(start = LocalTime.of(11, 0), end = LocalTime.of(12, 0))
        val other = TimeRange(start = LocalTime.of(12, 0), end = LocalTime.of(13, 0))

        When("overlaps를 호출하면") {
            Then("false를 반환한다") {
                range.overlaps(other) shouldBe false
            }
        }
    }
})
