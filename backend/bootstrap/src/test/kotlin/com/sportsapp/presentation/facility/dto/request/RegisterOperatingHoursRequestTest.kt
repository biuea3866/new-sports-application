package com.sportsapp.presentation.facility.dto.request

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class RegisterOperatingHoursRequestTest : BehaviorSpec({

    Given("요일별 운영시간 목록을 담은 요청이 주어졌을 때") {
        val request = RegisterOperatingHoursRequest(
            operatingHours = listOf(
                OperatingHoursRequest(
                    dayOfWeek = "MONDAY",
                    openTime = "06:00",
                    closeTime = "22:00",
                    capacity = 10,
                ),
            ),
        )

        When("toCommand를 호출하면") {
            val command = request.toCommand(facilityId = "f-001", ownerUserId = 1L)

            Then("facilityId·ownerUserId·변환된 운영시간이 command에 담긴다") {
                command.facilityId shouldBe "f-001"
                command.ownerUserId shouldBe 1L
                command.operatingHours shouldHaveSize 1
            }
        }
    }
})
