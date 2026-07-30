package com.sportsapp.domain.goods.entity

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class CartTest : BehaviorSpec({

    Given("userId로 Cart를 생성할 때") {
        val cart = Cart(userId = 42L)

        When("userId를 확인하면") {
            Then("[U-happy] userId가 올바르게 저장된다") {
                cart.userId shouldBe 42L
            }
        }
    }
})
