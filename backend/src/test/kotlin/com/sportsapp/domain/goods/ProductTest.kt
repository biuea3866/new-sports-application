package com.sportsapp.domain.goods

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class ProductTest : BehaviorSpec({

    Given("Product.create 팩토리 호출") {
        When("ownerUserId를 전달하면") {
            val product = Product.create(
                name = "테니스 라켓",
                category = ProductCategory.EQUIPMENT,
                price = BigDecimal("50000"),
                description = "고급 테니스 라켓",
                imageUrl = "https://example.com/racket.jpg",
                ownerUserId = 42L,
            )
            Then("[U-01] ownerId가 전달된 값으로 설정된다") {
                product.ownerId shouldBe 42L
            }
            Then("[U-02] 초기 status는 INACTIVE이다") {
                product.status shouldBe ProductStatus.INACTIVE
            }
        }

        When("ownerUserId가 0 이하이면") {
            Then("[U-03] IllegalArgumentException을 던진다") {
                shouldThrow<IllegalArgumentException> {
                    Product.create(
                        name = "라켓",
                        category = ProductCategory.EQUIPMENT,
                        price = BigDecimal("50000"),
                        description = "desc",
                        imageUrl = "https://example.com/racket.jpg",
                        ownerUserId = 0L,
                    )
                }
            }
        }
    }

    Given("ACTIVE 상태인 Product (deactivate 경로)") {
        val product = Product(
            name = "테니스 라켓",
            category = ProductCategory.EQUIPMENT,
            price = BigDecimal("50000"),
            description = "고급 테니스 라켓",
            imageUrl = "https://example.com/racket.jpg",
            status = ProductStatus.ACTIVE,
            ownerId = 1L,
        )

        When("deactivate를 호출하면") {
            product.deactivate()
            Then("[U-04] 상태가 INACTIVE가 된다") {
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
            ownerId = 1L,
        )

        When("activate를 호출하면") {
            product.activate()
            Then("[U-05] 상태가 ACTIVE가 된다") {
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
            ownerId = 1L,
        )

        When("activate를 다시 호출하면") {
            Then("[U-06 invalid transition] IllegalStateException을 던진다") {
                shouldThrow<IllegalStateException> { product.activate() }
            }
        }
    }

    Given("ownerId=1L 인 Product") {
        val product = Product(
            name = "테니스 라켓",
            category = ProductCategory.EQUIPMENT,
            price = BigDecimal("50000"),
            description = "고급 테니스 라켓",
            imageUrl = "https://example.com/racket.jpg",
            status = ProductStatus.INACTIVE,
            ownerId = 1L,
        )

        When("[B2B-10/U-01] activate() 호출 시") {
            product.activate()
            Then("status가 ACTIVE로 전이된다") {
                product.status shouldBe ProductStatus.ACTIVE
            }
        }

        When("[B2B-10/U-ownership-ok] 동일 ownerUserId로 requireOwnedBy 호출하면") {
            Then("예외 없이 통과한다") {
                io.kotest.assertions.throwables.shouldNotThrowAny {
                    product.requireOwnedBy(1L)
                }
            }
        }

        When("[B2B-10/U-ownership-fail] 다른 ownerUserId로 requireOwnedBy 호출하면") {
            Then("ResourceNotFoundException을 던진다") {
                shouldThrow<com.sportsapp.domain.common.exceptions.ResourceNotFoundException> {
                    product.requireOwnedBy(2L)
                }
            }
        }
    }
})
