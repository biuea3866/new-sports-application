package com.sportsapp.domain.goods.entity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import com.sportsapp.domain.goods.entity.ProductStatus
import com.sportsapp.domain.goods.vo.ProductCategory
import com.sportsapp.domain.goods.vo.SellerType
import com.sportsapp.domain.goods.exception.ProductInactiveException

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
                sellerType = SellerType.B2C,
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
                        sellerType = SellerType.B2C,
                    )
                }
            }
        }

        When("sellerType=B2B를 전달하면") {
            val product = Product.create(
                name = "브랜드 라켓",
                category = ProductCategory.EQUIPMENT,
                price = BigDecimal("50000"),
                description = "브랜드 공식 라켓",
                imageUrl = "https://example.com/racket.jpg",
                ownerUserId = 42L,
                sellerType = SellerType.B2B,
            )
            Then("sellerType이 B2B로 설정된다") {
                product.sellerType shouldBe SellerType.B2B
            }
        }

        When("sellerType=B2C를 전달하면") {
            val product = Product.create(
                name = "중고 라켓",
                category = ProductCategory.EQUIPMENT,
                price = BigDecimal("30000"),
                description = "중고 라켓",
                imageUrl = "https://example.com/racket.jpg",
                ownerUserId = 42L,
                sellerType = SellerType.B2C,
            )
            Then("sellerType이 B2C로 설정된다") {
                product.sellerType shouldBe SellerType.B2C
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

    Given("sellerType이 null인 백필 대상 Product") {
        val product = Product(
            name = "구형 상품",
            category = ProductCategory.EQUIPMENT,
            price = BigDecimal("10000"),
            description = "백필 전 상품",
            imageUrl = "https://example.com/legacy.jpg",
            status = ProductStatus.ACTIVE,
            sellerType = null,
            ownerId = 1L,
        )

        When("assignDefaultSellerTypeIfMissing을 호출하면") {
            product.assignDefaultSellerTypeIfMissing()
            Then("sellerType이 B2C로 채워진다") {
                product.sellerType shouldBe SellerType.B2C
            }
        }
    }

    Given("sellerType이 이미 B2B로 설정된 Product") {
        val product = Product(
            name = "브랜드 상품",
            category = ProductCategory.EQUIPMENT,
            price = BigDecimal("10000"),
            description = "듀얼라이트로 채워진 상품",
            imageUrl = "https://example.com/brand.jpg",
            status = ProductStatus.ACTIVE,
            sellerType = SellerType.B2B,
            ownerId = 1L,
        )

        When("assignDefaultSellerTypeIfMissing을 호출하면") {
            product.assignDefaultSellerTypeIfMissing()
            Then("기존 값(B2B)을 덮어쓰지 않는다 (멱등, no-op)") {
                product.sellerType shouldBe SellerType.B2B
            }
        }
    }
})
