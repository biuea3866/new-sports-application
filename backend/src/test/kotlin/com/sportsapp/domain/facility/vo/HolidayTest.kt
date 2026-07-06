package com.sportsapp.domain.facility.vo

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class HolidayTest : BehaviorSpec({

    Given("특정 날짜가 주어지면") {
        val date = LocalDate.of(2026, 7, 6)

        When("Holiday를 생성하면") {
            val holiday = Holiday(date = date)

            Then("해당 날짜를 그대로 보유한다") {
                holiday.date shouldBe date
            }
        }
    }
})
