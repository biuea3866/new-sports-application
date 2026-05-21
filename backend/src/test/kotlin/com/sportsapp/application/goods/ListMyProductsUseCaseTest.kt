package com.sportsapp.application.goods

import com.sportsapp.domain.common.security.OwnershipGuard
import com.sportsapp.domain.goods.GoodsDomainService
import com.sportsapp.domain.goods.Product
import com.sportsapp.domain.goods.ProductCategory
import com.sportsapp.domain.goods.ProductStatus
import com.sportsapp.domain.goods.ProductWithStock
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

class ListMyProductsUseCaseTest : BehaviorSpec({

    val goodsDomainService = mockk<GoodsDomainService>()
    val ownershipGuard = mockk<OwnershipGuard>()
    val useCase = ListMyProductsUseCase(goodsDomainService, ownershipGuard)


    Given("내 상품이 2건 존재할 때") {
        val ownerUserId = 42L
        val pageable = PageRequest.of(0, 10)

        val products = listOf(
            ProductWithStock(
                product = Product(
                    name = "라켓 A",
                    category = ProductCategory.EQUIPMENT,
                    price = BigDecimal("50000"),
                    description = "설명 A",
                    imageUrl = "https://example.com/a.jpg",
                    status = ProductStatus.ACTIVE,
                    ownerId = ownerUserId,
                ),
                stockQuantity = 5,
            ),
            ProductWithStock(
                product = Product(
                    name = "라켓 B",
                    category = ProductCategory.EQUIPMENT,
                    price = BigDecimal("30000"),
                    description = "설명 B",
                    imageUrl = "https://example.com/b.jpg",
                    status = ProductStatus.INACTIVE,
                    ownerId = ownerUserId,
                ),
                stockQuantity = 0,
            ),
        )

        every { ownershipGuard.authUserId() } returns ownerUserId
        every {
            goodsDomainService.listMyProducts(ownerUserId, pageable)
        } returns PageImpl(products, pageable, 2)

        When("[U-01] execute 호출하면") {
            val result = useCase.execute(pageable)

            Then("authUserId로 목록이 조회되고 2건이 반환된다") {
                verify { ownershipGuard.authUserId() }
                verify { goodsDomainService.listMyProducts(ownerUserId, pageable) }
                result.totalElements shouldBe 2
                result.content.size shouldBe 2
            }
        }
    }

    Given("내 상품이 없을 때") {
        val ownerUserId = 42L
        val pageable = PageRequest.of(0, 10)

        every { ownershipGuard.authUserId() } returns ownerUserId
        every {
            goodsDomainService.listMyProducts(ownerUserId, pageable)
        } returns PageImpl(emptyList(), pageable, 0)

        When("[U-02] execute 호출하면") {
            val result = useCase.execute(pageable)

            Then("빈 Page가 반환된다") {
                result.totalElements shouldBe 0
                result.content shouldBe emptyList()
            }
        }
    }
})
