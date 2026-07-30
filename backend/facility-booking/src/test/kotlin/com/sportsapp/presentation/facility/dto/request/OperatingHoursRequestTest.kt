package com.sportsapp.presentation.facility.dto.request

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.DayOfWeek
import java.time.LocalTime

class OperatingHoursRequestTest : BehaviorSpec({

    Given("브레이크가 포함된 운영시간 등록 요청이 주어졌을 때") {
        val request = OperatingHoursRequest(
            dayOfWeek = "MONDAY",
            openTime = "06:00",
            closeTime = "22:00",
            breaks = listOf(TimeRangeRequest(start = "12:00", end = "13:00")),
            slotDurationMinutes = 60,
            capacity = 10,
        )

        When("toVo를 호출하면") {
            val operatingHours = request.toVo()

            Then("도메인 VO로 정확히 변환된다") {
                operatingHours.dayOfWeek shouldBe DayOfWeek.MONDAY
                operatingHours.openTime shouldBe LocalTime.of(6, 0)
                operatingHours.closeTime shouldBe LocalTime.of(22, 0)
                operatingHours.breaks shouldHaveSize 1
                operatingHours.slotDurationMinutes shouldBe 60
                operatingHours.capacity shouldBe 10
            }
        }
    }
})
