package com.sportsapp.domain.community.vo

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class CommunityVisibilityTest : BehaviorSpec({

    Given("CommunityVisibility 값 집합") {
        When("전체 값을 조회하면") {
            val values = CommunityVisibility.entries.map { it.name }

            Then("TDD 상 정의된 PUBLIC·PRIVATE 두 값과 정확히 일치한다") {
                values shouldContainExactlyInAnyOrder listOf("PUBLIC", "PRIVATE")
            }
        }

        When("PUBLIC 값을 조회하면") {
            Then("PUBLIC enum 인스턴스와 동일하다") {
                CommunityVisibility.valueOf("PUBLIC") shouldBe CommunityVisibility.PUBLIC
            }
        }

        When("PRIVATE 값을 조회하면") {
            Then("PRIVATE enum 인스턴스와 동일하다") {
                CommunityVisibility.valueOf("PRIVATE") shouldBe CommunityVisibility.PRIVATE
            }
        }
    }
})
