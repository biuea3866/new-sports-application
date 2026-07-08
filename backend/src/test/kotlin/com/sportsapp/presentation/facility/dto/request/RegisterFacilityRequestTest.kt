package com.sportsapp.presentation.facility.dto.request

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class RegisterFacilityRequestTest : BehaviorSpec({

    fun buildRequest(sido: String? = null) = RegisterFacilityRequest(
        code = "GN-001",
        name = "강남 수영장",
        gu = "강남구",
        type = "수영장",
        address = "서울시 강남구",
        lat = 37.5,
        lng = 127.0,
        parking = true,
        tel = "02-0000-0000",
        homePage = "",
        eduYn = false,
        meta = emptyMap(),
        sido = sido,
    )

    Given("sido가 명시된 등록 요청이 주어졌을 때") {
        val request = buildRequest(sido = "부산")

        When("toCommand를 호출하면") {
            val command = request.toCommand(ownerUserId = 1L)

            Then("sido가 그대로 command에 전달된다") {
                command.sido shouldBe "부산"
            }
        }
    }

    Given("sido가 미입력된 등록 요청이 주어졌을 때") {
        val request = buildRequest(sido = null)

        When("toCommand를 호출하면") {
            val command = request.toCommand(ownerUserId = 1L)

            Then("command의 sido는 null이다") {
                command.sido.shouldBeNull()
            }
        }
    }
})
