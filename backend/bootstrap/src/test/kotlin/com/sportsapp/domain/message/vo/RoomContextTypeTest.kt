package com.sportsapp.domain.message.vo

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class RoomContextTypeTest : BehaviorSpec({

    Given("RoomContextType 값 집합") {
        When("전체 값을 조회하면") {
            val values = RoomContextType.entries.map { it.name }

            Then("TDD 상 정의된 COMMUNITY·GOODS_PRODUCT 두 값과 정확히 일치한다") {
                values shouldContainExactlyInAnyOrder listOf("COMMUNITY", "GOODS_PRODUCT")
            }
        }

        When("COMMUNITY 값을 조회하면") {
            Then("COMMUNITY enum 인스턴스와 동일하다") {
                RoomContextType.valueOf("COMMUNITY") shouldBe RoomContextType.COMMUNITY
            }
        }

        When("GOODS_PRODUCT 값을 조회하면") {
            Then("GOODS_PRODUCT enum 인스턴스와 동일하다") {
                RoomContextType.valueOf("GOODS_PRODUCT") shouldBe RoomContextType.GOODS_PRODUCT
            }
        }
    }
})
