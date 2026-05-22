package com.sportsapp.presentation.mcp

import com.sportsapp.application.goods.GetInventoryUseCase
import com.sportsapp.application.goods.InventoryResponse
import com.sportsapp.presentation.mcp.response.McpResponseStatus
import com.sportsapp.presentation.mcp.toolregistry.McpInventoryTools
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk

class McpInventoryToolsTest : BehaviorSpec({

    val getInventoryUseCase = mockk<GetInventoryUseCase>()
    val mcpInventoryTools = McpInventoryTools(getInventoryUseCase)

    Given("getInventory tool") {
        val inventoryResponse = InventoryResponse(
            ownerUserId = 10L,
            activeProductCount = 5L,
            outOfStockProductCount = 2L,
        )

        When("[U-03] ownerUserId로 getInventory를 호출하면") {
            every { getInventoryUseCase.execute(10L) } returns inventoryResponse

            val result = mcpInventoryTools.getInventory(ownerUserId = 10L)

            Then("[U-03] OK 상태와 재고 현황이 반환된다") {
                result.status shouldBe McpResponseStatus.OK
                result.data shouldNotBe null
                val data = requireNotNull(result.data)
                data.ownerUserId shouldBe 10L
                data.activeProductCount shouldBe 5L
                data.outOfStockProductCount shouldBe 2L
            }
        }

        When("[U-04] 재고가 없는 ownerUserId로 getInventory를 호출하면") {
            every { getInventoryUseCase.execute(99L) } returns InventoryResponse(
                ownerUserId = 99L,
                activeProductCount = 0L,
                outOfStockProductCount = 0L,
            )

            val result = mcpInventoryTools.getInventory(ownerUserId = 99L)

            Then("[U-04] OK 상태와 0 재고 현황이 반환된다") {
                result.status shouldBe McpResponseStatus.OK
                val data = requireNotNull(result.data)
                data.activeProductCount shouldBe 0L
                data.outOfStockProductCount shouldBe 0L
            }
        }
    }
})
