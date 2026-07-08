package com.sportsapp.domain.message.vo

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class ParticipantTypeTest : BehaviorSpec({

    Given("ParticipantType 값 집합") {
        When("전체 값을 조회하면") {
            val values = ParticipantType.entries.map { it.name }

            Then("TDD 상 정의된 MEMBER·GUEST 두 값과 정확히 일치한다") {
                values shouldContainExactlyInAnyOrder listOf("MEMBER", "GUEST")
            }
        }

        When("MEMBER 값을 조회하면") {
            Then("MEMBER enum 인스턴스와 동일하다") {
                ParticipantType.valueOf("MEMBER") shouldBe ParticipantType.MEMBER
            }
        }

        When("GUEST 값을 조회하면") {
            Then("GUEST enum 인스턴스와 동일하다") {
                ParticipantType.valueOf("GUEST") shouldBe ParticipantType.GUEST
            }
        }
    }
})
