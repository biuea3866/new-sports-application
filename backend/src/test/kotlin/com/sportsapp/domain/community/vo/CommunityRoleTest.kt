package com.sportsapp.domain.community.vo

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class CommunityRoleTest : BehaviorSpec({

    Given("CommunityRole 값 집합") {
        When("전체 값을 조회하면") {
            val values = CommunityRole.entries.map { it.name }

            Then("TDD 상 정의된 HOST·MEMBER 두 값과 정확히 일치한다") {
                values shouldContainExactlyInAnyOrder listOf("HOST", "MEMBER")
            }
        }

        When("HOST 값을 조회하면") {
            Then("HOST enum 인스턴스와 동일하다") {
                CommunityRole.valueOf("HOST") shouldBe CommunityRole.HOST
            }
        }

        When("MEMBER 값을 조회하면") {
            Then("MEMBER enum 인스턴스와 동일하다") {
                CommunityRole.valueOf("MEMBER") shouldBe CommunityRole.MEMBER
            }
        }
    }
})
