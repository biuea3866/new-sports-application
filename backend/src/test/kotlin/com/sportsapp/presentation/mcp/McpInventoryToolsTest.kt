package com.sportsapp.presentation.mcp

import com.sportsapp.application.goods.GetInventoryUseCase
import com.sportsapp.application.goods.InventoryResponse
import com.sportsapp.domain.mcp.McpAuthenticatedPrincipal
import com.sportsapp.domain.mcp.McpScope
import com.sportsapp.presentation.mcp.response.McpResponseStatus
import com.sportsapp.presentation.mcp.toolregistry.McpInventoryTools
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

class McpInventoryToolsTest : BehaviorSpec({

    val getInventoryUseCase = mockk<GetInventoryUseCase>()
    val mcpInventoryTools = McpInventoryTools(getInventoryUseCase)

    fun setupPrincipal(userId: Long) {
        val principal = object : McpAuthenticatedPrincipal {
            override val tokenId = 100L
            override val userId = userId
            override val grantedScopes = setOf<McpScope>()
        }
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(principal, null, emptyList())
    }

    afterEach { SecurityContextHolder.clearContext() }

    Given("getInventory tool") {
        val inventoryResponse = InventoryResponse(
            ownerUserId = 10L,
            activeProductCount = 5L,
            outOfStockProductCount = 2L,
        )

        When("[U-05] 인증된 운영자(userId=10)로 getInventory를 호출하면") {
            setupPrincipal(10L)
            every { getInventoryUseCase.execute(10L) } returns inventoryResponse

            val result = mcpInventoryTools.getInventory()

            Then("[U-05] OK 상태와 재고 현황이 반환된다") {
                result.status shouldBe McpResponseStatus.OK
                result.data shouldNotBe null
                val data = requireNotNull(result.data)
                data.ownerUserId shouldBe 10L
                data.activeProductCount shouldBe 5L
                data.outOfStockProductCount shouldBe 2L
            }
        }

        When("[U-06] getInventory를 호출하면 UseCase가 principal.userId를 인자로 호출된다") {
            setupPrincipal(10L)
            every { getInventoryUseCase.execute(10L) } returns inventoryResponse

            mcpInventoryTools.getInventory()

            Then("[U-06] GetInventoryUseCase.execute(10L)가 호출된다") {
                verify { getInventoryUseCase.execute(10L) }
            }
        }

        When("[U-07] 재고가 없는 운영자(userId=99)로 getInventory를 호출하면") {
            setupPrincipal(99L)
            every { getInventoryUseCase.execute(99L) } returns InventoryResponse(
                ownerUserId = 99L,
                activeProductCount = 0L,
                outOfStockProductCount = 0L,
            )

            val result = mcpInventoryTools.getInventory()

            Then("[U-07] OK 상태와 0 재고 현황이 반환된다") {
                result.status shouldBe McpResponseStatus.OK
                val data = requireNotNull(result.data)
                data.activeProductCount shouldBe 0L
                data.outOfStockProductCount shouldBe 0L
            }
        }

        When("[U-08] IDOR — principal이 없으면 (SecurityContext 비어있음)") {
            SecurityContextHolder.clearContext()
            val localUseCase = mockk<GetInventoryUseCase>()
            val localTools = McpInventoryTools(localUseCase)

            Then("[U-08] AccessDeniedException이 발생하고 UseCase는 호출되지 않는다") {
                shouldThrow<AccessDeniedException> {
                    localTools.getInventory()
                }
                verify(exactly = 0) { localUseCase.execute(any()) }
            }
        }
    }
})
