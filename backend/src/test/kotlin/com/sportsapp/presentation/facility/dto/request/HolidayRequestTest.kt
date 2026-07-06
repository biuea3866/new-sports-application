package com.sportsapp.presentation.facility.dto.request

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class HolidayRequestTest : BehaviorSpec({

    Given("휴무일 추가 요청이 주어졌을 때") {
        val request = HolidayRequest(date = "2026-07-06")

        When("toCommand를 호출하면") {
            val command = request.toCommand(facilityId = "f-001", ownerUserId = 1L)

            Then("facilityId·ownerUserId·파싱된 날짜가 command에 담긴다") {
                command.facilityId shouldBe "f-001"
                command.ownerUserId shouldBe 1L
                command.date shouldBe LocalDate.of(2026, 7, 6)
            }
        }
    }
})
