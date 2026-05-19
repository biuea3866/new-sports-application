package com.sportsapp.domain.goods

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class ProductTest : BehaviorSpec({

    Given("ACTIVE 상태인 Product (deactivate 경로)") {
        val product = Product(
            name = "테니스 라켓",
            category = ProductCategory.EQUIPMENT,
            price = BigDecimal("50000"),
            description = "고급 테니스 라켓",
            imageUrl = "https://example.com/racket.jpg",
            status = ProductStatus.ACTIVE,
        )

        When("deactivate를 호출하면") {
            product.deactivate()
            Then("[U-03] 상태가 INACTIVE가 된다") {
                product.status shouldBe ProductStatus.INACTIVE
            }
        }
    }

    Given("INACTIVE 상태인 Product") {
        val product = Product(
            name = "테니스 라켓",
            category = ProductCategory.EQUIPMENT,
            price = BigDecimal("50000"),
            description = "고급 테니스 라켓",
            imageUrl = "https://example.com/racket.jpg",
            status = ProductStatus.INACTIVE,
        )

        When("activate를 호출하면") {
            product.activate()
            Then("[U-03] 상태가 ACTIVE가 된다") {
                product.status shouldBe ProductStatus.ACTIVE
            }
        }
    }

    Given("ACTIVE 상태인 Product (중복 전이 경로)") {
        val product = Product(
            name = "테니스 라켓",
            category = ProductCategory.EQUIPMENT,
            price = BigDecimal("50000"),
            description = "고급 테니스 라켓",
            imageUrl = "https://example.com/racket.jpg",
            status = ProductStatus.ACTIVE,
        )

        When("activate를 다시 호출하면") {
            Then("[U-03 invalid transition] IllegalStateException을 던진다") {
                shouldThrow<IllegalStateException> { product.activate() }
            }
        }
    }
})
