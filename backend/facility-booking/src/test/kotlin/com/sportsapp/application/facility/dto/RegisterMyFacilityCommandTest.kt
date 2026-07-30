package com.sportsapp.application.facility.dto

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class RegisterMyFacilityCommandTest : BehaviorSpec({

    fun buildCommand(sido: String?) = RegisterMyFacilityCommand(
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
        ownerUserId = 1L,
        sido = sido,
    )

    Given("sido가 명시된 command가 주어졌을 때") {
        val command = buildCommand(sido = "부산")

        When("toAttributes를 호출하면") {
            val attributes = command.toAttributes()

            Then("sido 값이 attributes.sidoHint로 전달되고 region은 미해석 상태다") {
                attributes.sidoHint shouldBe "부산"
                attributes.region.isUnspecified() shouldBe true
            }
        }
    }

    Given("sido가 미입력된 command가 주어졌을 때") {
        val command = buildCommand(sido = null)

        When("toAttributes를 호출하면") {
            val attributes = command.toAttributes()

            Then("attributes.sidoHint는 null이다") {
                attributes.sidoHint.shouldBeNull()
            }
        }
    }
})
