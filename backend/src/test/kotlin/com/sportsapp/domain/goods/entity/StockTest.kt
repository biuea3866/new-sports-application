package com.sportsapp.domain.goods.entity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import com.sportsapp.domain.goods.exception.OutOfStockException
import com.sportsapp.domain.goods.exception.InvalidQuantityException

class StockTest : BehaviorSpec({

    Given("재고가 10개인 Stock") {
        val stock = Stock(productId = 1L, quantity = 10)

        When("3개를 차감하면") {
            stock.deduct(3)
            Then("[U-01 happy] quantity가 7이 된다") {
                stock.quantity shouldBe 7
            }
        }
    }

    Given("재고가 5개인 Stock") {
        When("10개를 차감하면") {
            Then("[U-01] OutOfStockException을 던지고 quantity는 변하지 않는다") {
                val stock = Stock(productId = 1L, quantity = 5)
                shouldThrow<OutOfStockException> { stock.deduct(10) }
                stock.quantity shouldBe 5
            }
        }

        When("정확히 5개를 차감하면") {
            Then("[U-01 boundary] quantity가 0이 된다") {
                val stock = Stock(productId = 1L, quantity = 5)
                stock.deduct(5)
                stock.quantity shouldBe 0
            }
        }

        When("0을 차감하면") {
            Then("[U-01 boundary] InvalidQuantityException을 던지고 quantity는 변하지 않는다") {
                val stock = Stock(productId = 1L, quantity = 5)
                shouldThrow<InvalidQuantityException> { stock.deduct(0) }
                stock.quantity shouldBe 5
            }
        }

        When("음수를 차감하면") {
            Then("[U-01] InvalidQuantityException을 던지고 quantity는 변하지 않는다") {
                val stock = Stock(productId = 1L, quantity = 5)
                shouldThrow<InvalidQuantityException> { stock.deduct(-3) }
                stock.quantity shouldBe 5
            }
        }
    }

    Given("재고가 5개인 Stock에 restore를 호출할 때") {
        When("3개를 복구하면") {
            Then("[U-02] quantity가 8이 된다") {
                val stock = Stock(productId = 1L, quantity = 5)
                stock.restore(3)
                stock.quantity shouldBe 8
            }
        }

        When("음수를 복구하면") {
            Then("[U-02] InvalidQuantityException을 던진다") {
                val stock = Stock(productId = 1L, quantity = 5)
                shouldThrow<InvalidQuantityException> { stock.restore(-1) }
            }
        }

        When("0을 복구하면") {
            Then("[U-02 boundary] InvalidQuantityException을 던진다") {
                val stock = Stock(productId = 1L, quantity = 5)
                shouldThrow<InvalidQuantityException> { stock.restore(0) }
            }
        }
    }
})
