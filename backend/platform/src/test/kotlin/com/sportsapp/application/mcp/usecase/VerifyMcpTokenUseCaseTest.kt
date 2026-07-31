package com.sportsapp.application.mcp.usecase

import com.sportsapp.application.mcp.dto.VerifyMcpTokenCommand
import com.sportsapp.domain.mcp.service.McpTokenDomainService
import com.sportsapp.domain.mcp.vo.McpScope
import com.sportsapp.domain.mcp.vo.McpTokenVerification
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class VerifyMcpTokenUseCaseTest : BehaviorSpec({

    val mcpTokenDomainService = mockk<McpTokenDomainService>()
    val useCase = VerifyMcpTokenUseCase(mcpTokenDomainService)

    Given("유효한 평문 토큰으로 검증을 요청하면") {
        val command = VerifyMcpTokenCommand(plainToken = "mcp_42_random")
        every { mcpTokenDomainService.verifyToken("mcp_42_random") } returns McpTokenVerification.valid(
            tokenId = 42L,
            userId = 7L,
            scopes = setOf(McpScope(verb = "read", domain = "facility", qualifier = null)),
        )

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("valid=true와 주체 식별자·스코프가 담긴 응답이 반환된다") {
                result.valid shouldBe true
                result.tokenId shouldBe 42L
                result.userId shouldBe 7L
                result.scopes.shouldContainExactly(listOf("read:facility"))
                verify(exactly = 1) { mcpTokenDomainService.verifyToken("mcp_42_random") }
            }
        }
    }

    Given("존재하지 않거나 만료된 평문 토큰으로 검증을 요청하면") {
        val command = VerifyMcpTokenCommand(plainToken = "mcp_999_random")
        every { mcpTokenDomainService.verifyToken("mcp_999_random") } returns McpTokenVerification.invalid()

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("예외 없이 valid=false 응답이 반환된다 (실패 경로)") {
                result.valid shouldBe false
                result.tokenId shouldBe null
                result.userId shouldBe null
                result.scopes.shouldContainExactly(emptyList())
            }
        }
    }
})
