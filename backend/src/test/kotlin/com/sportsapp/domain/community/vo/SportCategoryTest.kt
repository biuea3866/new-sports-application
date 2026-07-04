package com.sportsapp.domain.community.vo

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class SportCategoryTest : BehaviorSpec({

    Given("SportCategory 값 집합") {
        When("전체 값을 조회하면") {
            val values = SportCategory.entries.map { it.name }

            Then("커뮤니티 개설에 필요한 종목 카테고리 집합과 정확히 일치한다") {
                values shouldContainExactlyInAnyOrder listOf(
                    "SOCCER",
                    "BASKETBALL",
                    "BASEBALL",
                    "TENNIS",
                    "BADMINTON",
                    "GOLF",
                    "RUNNING",
                    "CYCLING",
                    "SWIMMING",
                    "HIKING",
                    "YOGA",
                    "ETC",
                )
            }
        }

        When("ETC 값을 조회하면") {
            Then("ETC enum 인스턴스와 동일하다 (분류 안 되는 종목의 기본값)") {
                SportCategory.valueOf("ETC") shouldBe SportCategory.ETC
            }
        }
    }
})
