package com.sportsapp.domain.goods.entity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import com.sportsapp.domain.goods.exception.InvalidQuantityException

class CartItemTest : BehaviorSpec({

    Given("quantity 3짜리 CartItem이 있을 때") {
        val cartItem = CartItem(cartId = 1L, productId = 10L, quantity = 3)

        When("addQuantity(2)를 호출하면") {
            Then("[U-02 merge] quantity가 5로 병합된다") {
                cartItem.addQuantity(2)
                cartItem.quantity shouldBe 5
            }
        }

        When("addQuantity(0)을 호출하면") {
            Then("[U-02 invalid] IllegalArgumentException이 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    cartItem.addQuantity(0)
                }
            }
        }

        When("updateQuantity(10)을 호출하면") {
            Then("[U-happy] quantity가 10으로 변경된다") {
                cartItem.updateQuantity(10)
                cartItem.quantity shouldBe 10
            }
        }

        When("updateQuantity(0)을 호출하면") {
            Then("[U-01 invalid] IllegalArgumentException이 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    cartItem.updateQuantity(0)
                }
            }
        }
    }
})
