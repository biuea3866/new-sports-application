package com.sportsapp.domain.goods

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class GoodsOrderTest : BehaviorSpec({

    fun pendingOrder(userId: Long = 1L, amount: BigDecimal = BigDecimal("10000")) =
        GoodsOrder.create(userId, amount)

    Given("PENDING 상태의 주문") {
        When("markPaid를 호출하면") {
            Then("[U-01] 상태가 CONFIRMED로 전이되고 paymentId가 저장된다") {
                val order = pendingOrder()
                order.markPaid(99L)
                order.status shouldBe GoodsOrderStatus.CONFIRMED
                order.paymentId shouldBe 99L
            }
        }

        When("cancel을 호출하면") {
            Then("[U-01] 상태가 CANCELLED로 전이된다") {
                val order = pendingOrder()
                order.cancel()
                order.status shouldBe GoodsOrderStatus.CANCELLED
            }
        }
    }

    Given("CONFIRMED 상태의 주문") {
        fun confirmedOrder(): GoodsOrder {
            val order = pendingOrder()
            order.markPaid(10L)
            return order
        }

        When("markShipped를 호출하면") {
            Then("[U-01] 상태가 SHIPPED로 전이된다") {
                val order = confirmedOrder()
                order.markShipped()
                order.status shouldBe GoodsOrderStatus.SHIPPED
            }
        }

        When("cancel을 호출하면") {
            Then("[U-01] 상태가 CANCELLED로 전이된다") {
                val order = confirmedOrder()
                order.cancel()
                order.status shouldBe GoodsOrderStatus.CANCELLED
            }
        }

        When("markPaid를 다시 호출하면") {
            Then("[U-02] InvalidGoodsOrderStateException이 발생한다") {
                val order = confirmedOrder()
                shouldThrow<InvalidGoodsOrderStateException> { order.markPaid(20L) }
            }
        }
    }

    Given("CANCELLED 상태의 주문") {
        fun cancelledOrder(): GoodsOrder {
            val order = pendingOrder()
            order.cancel()
            return order
        }

        When("markPaid를 호출하면") {
            Then("[U-02] InvalidGoodsOrderStateException이 발생한다") {
                val order = cancelledOrder()
                shouldThrow<InvalidGoodsOrderStateException> { order.markPaid(5L) }
            }
        }

        When("cancel을 다시 호출하면") {
            Then("[U-02] InvalidGoodsOrderStateException이 발생한다") {
                val order = cancelledOrder()
                shouldThrow<InvalidGoodsOrderStateException> { order.cancel() }
            }
        }
    }

    Given("다른 사용자의 주문에") {
        When("requireOwnedBy를 호출하면") {
            Then("[U-03] NotGoodsOrderOwnerException이 발생한다") {
                val order = pendingOrder(userId = 1L)
                shouldThrow<NotGoodsOrderOwnerException> { order.requireOwnedBy(2L) }
            }
        }

        When("본인이 requireOwnedBy를 호출하면") {
            Then("[U-03] 예외 없이 통과한다") {
                val order = pendingOrder(userId = 1L)
                order.requireOwnedBy(1L)
            }
        }
    }
})
