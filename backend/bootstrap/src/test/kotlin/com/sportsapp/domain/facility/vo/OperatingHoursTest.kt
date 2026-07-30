package com.sportsapp.domain.facility.vo

import com.sportsapp.domain.facility.exception.InvalidFacilityException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class OperatingHoursTest : BehaviorSpec({

    Given("06:00~22:00, 브레이크 12:00~13:00, 60분 단위 운영시간이 등록됐을 때") {
        val operatingHours = OperatingHours(
            dayOfWeek = DayOfWeek.MONDAY,
            openTime = LocalTime.of(6, 0),
            closeTime = LocalTime.of(22, 0),
            breaks = listOf(TimeRange(start = LocalTime.of(12, 0), end = LocalTime.of(13, 0))),
            slotDurationMinutes = 60,
            capacity = 10,
        )
        val monday = LocalDate.of(2026, 7, 6) // 2026-07-06은 월요일

        When("slotRangesFor를 해당 요일 날짜로 호출하면") {
            val slots = operatingHours.slotRangesFor(monday)

            Then("브레이크를 제외한 시간대 목록을 반환한다") {
                slots shouldHaveSize 15
                slots shouldContain TimeRange(start = LocalTime.of(6, 0), end = LocalTime.of(7, 0))
                slots shouldContain TimeRange(start = LocalTime.of(21, 0), end = LocalTime.of(22, 0))
            }

            Then("브레이크 경계(12:00·13:00)에 걸친 슬롯만 정확히 제외된다") {
                slots shouldNotContain TimeRange(start = LocalTime.of(12, 0), end = LocalTime.of(13, 0))
                slots shouldContain TimeRange(start = LocalTime.of(11, 0), end = LocalTime.of(12, 0))
                slots shouldContain TimeRange(start = LocalTime.of(13, 0), end = LocalTime.of(14, 0))
            }
        }

        When("slotRangesFor를 다른 요일 날짜로 호출하면") {
            val tuesday = monday.plusDays(1)
            val slots = operatingHours.slotRangesFor(tuesday)

            Then("빈 목록을 반환한다(에러 아님)") {
                slots.shouldBeEmpty()
            }
        }
    }

    Given("openTime이 closeTime보다 늦은 값으로 생성하면") {
        When("OperatingHours를 생성하면") {
            Then("InvalidFacilityException을 던진다") {
                shouldThrow<InvalidFacilityException> {
                    OperatingHours(
                        dayOfWeek = DayOfWeek.MONDAY,
                        openTime = LocalTime.of(22, 0),
                        closeTime = LocalTime.of(6, 0),
                        capacity = 10,
                    )
                }
            }
        }
    }

    Given("slotDurationMinutes가 0 이하로 생성하면") {
        When("OperatingHours를 생성하면") {
            Then("InvalidFacilityException을 던진다") {
                shouldThrow<InvalidFacilityException> {
                    OperatingHours(
                        dayOfWeek = DayOfWeek.MONDAY,
                        openTime = LocalTime.of(6, 0),
                        closeTime = LocalTime.of(22, 0),
                        slotDurationMinutes = 0,
                        capacity = 10,
                    )
                }
            }
        }
    }

    Given("capacity가 0 이하로 생성하면") {
        When("OperatingHours를 생성하면") {
            Then("InvalidFacilityException을 던진다") {
                shouldThrow<InvalidFacilityException> {
                    OperatingHours(
                        dayOfWeek = DayOfWeek.MONDAY,
                        openTime = LocalTime.of(6, 0),
                        closeTime = LocalTime.of(22, 0),
                        capacity = 0,
                    )
                }
            }
        }
    }
})
