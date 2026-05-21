package com.sportsapp.application.goods

import com.sportsapp.domain.goods.GoodsDomainService
import com.sportsapp.domain.goods.InventoryItem
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class GetInventoryUseCaseTest : BehaviorSpec({

    val goodsDomainService = mockk<GoodsDomainService>()
    val useCase = GetInventoryUseCase(goodsDomainService)

    Given("[U-03] lowStockOnly=true 플래그로 조회") {
        val items = listOf(
            InventoryItem(productId = 1L, productName = "상품A", stockQuantity = 3, lowStock = true),
            InventoryItem(productId = 2L, productName = "상품B", stockQuantity = 2, lowStock = true),
        )
        every { goodsDomainService.findInventory(ownerUserId = 10L, lowStockOnly = true) } returns items

        When("[U-03] execute 호출 시") {
            val command = GetInventoryCommand(operatorUserId = 10L, lowStockOnly = true)
            val response = useCase.execute(command)

            Then("[U-03] lowStockOnly=true 플래그를 DomainService에 전달한다") {
                verify { goodsDomainService.findInventory(ownerUserId = 10L, lowStockOnly = true) }
                response.items.size shouldBe 2
                response.items.all { it.lowStock } shouldBe true
            }
        }
    }

    Given("[U-03b] lowStockOnly=false 플래그로 전체 재고 조회") {
        val items = listOf(
            InventoryItem(productId = 1L, productName = "상품A", stockQuantity = 100, lowStock = false),
            InventoryItem(productId = 2L, productName = "상품B", stockQuantity = 3, lowStock = true),
        )
        every { goodsDomainService.findInventory(ownerUserId = 10L, lowStockOnly = false) } returns items

        When("[U-03b] execute 호출 시") {
            val command = GetInventoryCommand(operatorUserId = 10L, lowStockOnly = false)
            val response = useCase.execute(command)

            Then("[U-03b] DomainService에 lowStockOnly=false를 전달한다") {
                verify { goodsDomainService.findInventory(ownerUserId = 10L, lowStockOnly = false) }
                response.items.size shouldBe 2
            }
        }
    }
})
