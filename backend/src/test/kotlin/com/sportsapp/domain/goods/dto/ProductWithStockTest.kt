package com.sportsapp.domain.goods.dto

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.exception.LimitedDropQuantityExceedsStockException
import com.sportsapp.domain.goods.vo.ProductCategory
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

private const val OWNER_USER_ID = 500L
private const val OTHER_USER_ID = 999L

class ProductWithStockTest : BehaviorSpec({

    fun productWithStock(stockQuantity: Int): ProductWithStock {
        val product = Product.create(
            name = "한정판 스니커즈",
            category = ProductCategory.FOOTWEAR,
            price = BigDecimal("50000"),
            description = "설명",
            imageUrl = "https://image",
            ownerUserId = OWNER_USER_ID,
        )
        return ProductWithStock(product = product, stockQuantity = stockQuantity)
    }

    Given("소유자 본인이 요청한 상황") {
        val target = productWithStock(stockQuantity = 50)

        When("requireOwnedBy를 호출하면") {
            Then("예외를 던지지 않는다") {
                shouldNotThrowAny { target.requireOwnedBy(OWNER_USER_ID) }
            }
        }
    }

    Given("소유자가 아닌 사용자가 요청한 상황") {
        val target = productWithStock(stockQuantity = 50)

        When("requireOwnedBy를 호출하면") {
            Then("ResourceNotFoundException을 던진다") {
                shouldThrow<ResourceNotFoundException> { target.requireOwnedBy(OTHER_USER_ID) }
            }
        }
    }

    Given("재고 이내의 수량을 요청한 상황") {
        val target = productWithStock(stockQuantity = 50)

        When("validateQuantityWithin을 호출하면") {
            Then("예외를 던지지 않는다") {
                shouldNotThrowAny { target.validateQuantityWithin(30) }
            }
        }
    }

    Given("재고를 초과하는 수량을 요청한 상황") {
        val target = productWithStock(stockQuantity = 10)

        When("validateQuantityWithin을 호출하면") {
            Then("LimitedDropQuantityExceedsStockException을 던진다") {
                shouldThrow<LimitedDropQuantityExceedsStockException> { target.validateQuantityWithin(20) }
            }
        }
    }

    Given("가격이 50000원인 Product를 보유한 상황") {
        val target = productWithStock(stockQuantity = 50)

        When("price를 조회하면") {
            Then("product.price를 그대로 반환한다") {
                target.price shouldBe BigDecimal("50000")
            }
        }
    }

    Given("limitedDropId 없이 생성된 상황") {
        val target = productWithStock(stockQuantity = 50)

        When("limitedDropId를 조회하면") {
            Then("기본값 null을 반환한다") {
                target.limitedDropId shouldBe null
            }
        }
    }

    Given("활성 한정판 회차 id가 결합된 상황") {
        val target = productWithStock(stockQuantity = 50).copy(limitedDropId = 7L)

        When("limitedDropId를 조회하면") {
            Then("결합된 값을 그대로 반환한다") {
                target.limitedDropId shouldBe 7L
            }
        }
    }
})
