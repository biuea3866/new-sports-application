package com.sportsapp.application.facility.dto

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class FacilityCriteriaTest : BehaviorSpec({

    Given("sidoCode가 공백 문자열인 경우") {
        val criteria = FacilityCriteria(sidoCode = "  ", sigunguCode = null, gu = null, type = null, page = 0, size = 50)

        When("effectiveSidoCode를 호출하면") {
            Then("null을 반환한다") {
                criteria.effectiveSidoCode() shouldBe null
            }
        }
    }

    Given("sigunguCode가 공백 문자열인 경우") {
        val criteria = FacilityCriteria(sidoCode = null, sigunguCode = "   ", gu = null, type = null, page = 0, size = 50)

        When("effectiveSigunguCode를 호출하면") {
            Then("null을 반환한다") {
                criteria.effectiveSigunguCode() shouldBe null
            }
        }
    }

    Given("sidoCode·sigunguCode가 유효한 값인 경우") {
        val criteria = FacilityCriteria(sidoCode = "26", sigunguCode = "26410", gu = null, type = null, page = 0, size = 50)

        When("effectiveSidoCode·effectiveSigunguCode를 호출하면") {
            Then("입력값 그대로 반환한다") {
                criteria.effectiveSidoCode() shouldBe "26"
                criteria.effectiveSigunguCode() shouldBe "26410"
            }
        }
    }

    Given("sidoCode·sigunguCode가 null인 경우") {
        val criteria = FacilityCriteria(sidoCode = null, sigunguCode = null, gu = "강남구", type = null, page = 0, size = 50)

        When("effectiveSidoCode·effectiveSigunguCode를 호출하면") {
            Then("null을 반환하고 gu는 영향받지 않는다") {
                criteria.effectiveSidoCode() shouldBe null
                criteria.effectiveSigunguCode() shouldBe null
                criteria.effectiveGu() shouldBe "강남구"
            }
        }
    }
})
