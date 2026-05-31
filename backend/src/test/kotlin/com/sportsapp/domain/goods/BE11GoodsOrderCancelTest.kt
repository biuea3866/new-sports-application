package com.sportsapp.domain.goods

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class BE11GoodsOrderCancelTest : BehaviorSpec({

    fun pendingOrder() = GoodsOrder.create(userId = 1L, totalAmount = BigDecimal("10000"))

    Given("[U-01] PENDING 상태의 GoodsOrder") {
        val order = pendingOrder()

        When("cancel()을 호출하면") {
            order.cancel()

            Then("[U-01] 상태가 CANCELLED로 전이된다") {
                order.status shouldBe GoodsOrderStatus.CANCELLED
            }
        }
    }

    Given("[U-02] CANCELLED 상태의 GoodsOrder") {
        val order = pendingOrder()
        order.cancel()

        When("cancel()을 다시 호출하면") {
            Then("[U-02] InvalidGoodsOrderStateException을 던진다") {
                shouldThrow<InvalidGoodsOrderStateException> {
                    order.cancel()
                }
            }
        }
    }
})
