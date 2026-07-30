package com.sportsapp.presentation.facility.dto.request

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class UpdateFacilityRequestTest : BehaviorSpec({

    Given("sido가 함께 전달된 수정 요청이 주어졌을 때") {
        val request = UpdateFacilityRequest(meta = mapOf("key" to "value"), sido = "부산")

        When("toCommand를 호출하면") {
            val command = request.toCommand(facilityId = "f-001", ownerUserId = 1L)

            Then("sido가 그대로 command에 전달된다") {
                command.sido shouldBe "부산"
                command.patch["key"] shouldBe "value"
            }
        }
    }

    Given("sido 없이 meta만 전달된 수정 요청이 주어졌을 때") {
        val request = UpdateFacilityRequest(meta = mapOf("key" to "value"))

        When("toCommand를 호출하면") {
            val command = request.toCommand(facilityId = "f-001", ownerUserId = 1L)

            Then("command의 sido는 null이다") {
                command.sido.shouldBeNull()
            }
        }
    }
})
