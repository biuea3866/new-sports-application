package com.sportsapp.presentation.facility.dto.request

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class LegacyRowRequestTest : BehaviorSpec({

    fun buildRequest(sido: String? = null) = LegacyRowRequest(
        legacyId = "LEGACY-001",
        name = "테스트 시설",
        gu = "강남구",
        type = "수영장",
        address = "서울시 강남구",
        ycode = "37.5",
        xcode = "127.0",
        parking = true,
        tel = "02-0000-0000",
        homePage = "",
        eduYn = false,
        extraFields = emptyMap(),
        sido = sido,
    )

    Given("CSV sido 컬럼 값이 채워진 요청이 주어졌을 때") {
        val request = buildRequest(sido = "부산")

        When("toLegacyRow를 호출하면") {
            val row = request.toLegacyRow()

            Then("sido 값이 그대로 LegacyRow에 전달된다") {
                row.sido shouldBe "부산"
            }
        }
    }

    Given("CSV sido 컬럼 값이 비어 있는 요청이 주어졌을 때") {
        val request = buildRequest(sido = null)

        When("toLegacyRow를 호출하면") {
            val row = request.toLegacyRow()

            Then("LegacyRow의 sido는 null이다") {
                row.sido.shouldBeNull()
            }
        }
    }
})
