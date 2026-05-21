package com.sportsapp.application.mcp

import com.sportsapp.domain.mcp.IssueMcpTokenCommand
import com.sportsapp.domain.mcp.McpToken
import com.sportsapp.domain.mcp.McpTokenDomainService
import com.sportsapp.domain.mcp.McpTokenStatus
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime

class IssueMcpTokenUseCaseTest : BehaviorSpec({

    val mcpTokenDomainService = mockk<McpTokenDomainService>()
    val useCase = IssueMcpTokenUseCase(mcpTokenDomainService)

    fun makeToken(id: Long = 1L): McpToken {
        val token = McpToken.create(
            userId = 1L,
            name = "my-token",
            tokenHash = "hashed",
            expiresAt = null,
        )
        val idField = McpToken::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(token, id)
        val superclass = token.javaClass.superclass
        listOf("createdAt", "updatedAt").forEach { fieldName ->
            val field = superclass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(token, ZonedDateTime.now())
        }
        return token
    }

    Given("[U-01] 유효한 command로 토큰 발급을 요청하면") {
        val command = IssueMcpTokenUseCaseCommand(
            userId = 1L,
            name = "my-token",
            scopes = listOf("read:facility"),
            expiresAt = null,
        )
        val token = makeToken()
        val issueMcpTokenCommand = IssueMcpTokenCommand(
            userId = 1L,
            name = "my-token",
            scopes = listOf("read:facility"),
            expiresAt = null,
        )
        every {
            mcpTokenDomainService.issueToken(any())
        } returns McpTokenDomainService.IssueResult(plainToken = "plain-abc123", token = token)

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("[U-01] 발급된 토큰 정보와 평문 토큰이 포함된 응답이 반환된다") {
                result.tokenId shouldBe 1L
                result.plainToken shouldBe "plain-abc123"
                result.status shouldBe McpTokenStatus.ACTIVE
                verify(exactly = 1) { mcpTokenDomainService.issueToken(any()) }
            }
        }
    }
})
