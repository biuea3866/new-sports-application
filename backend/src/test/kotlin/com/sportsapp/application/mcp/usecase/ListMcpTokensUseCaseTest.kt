package com.sportsapp.application.mcp.usecase

import com.sportsapp.domain.mcp.entity.McpToken
import com.sportsapp.domain.mcp.service.McpTokenDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.ZonedDateTime

class ListMcpTokensUseCaseTest : BehaviorSpec({

    val mcpTokenDomainService = mockk<McpTokenDomainService>()
    val useCase = ListMcpTokensUseCase(mcpTokenDomainService)

    fun makeToken(id: Long, name: String): McpToken {
        val token = McpToken.create(
            userId = 1L,
            name = name,
            tokenHash = "hash-$id",
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

    Given("[U-02] 활성 토큰이 2건 있는 userId로 조회하면") {
        val tokens = listOf(makeToken(1L, "token-a"), makeToken(2L, "token-b"))
        every { mcpTokenDomainService.listMyTokens(1L) } returns tokens

        When("execute를 호출하면") {
            val result = useCase.execute(userId = 1L)

            Then("[U-02] 2건의 토큰 정보가 포함된 응답 목록이 반환된다") {
                result.tokens.size shouldBe 2
                result.tokens[0].tokenId shouldBe 1L
                result.tokens[1].tokenId shouldBe 2L
            }
        }
    }

    Given("[U-03] 활성 토큰이 없는 userId로 조회하면") {
        every { mcpTokenDomainService.listMyTokens(99L) } returns emptyList()

        When("execute를 호출하면") {
            val result = useCase.execute(userId = 99L)

            Then("[U-03] 빈 목록이 반환된다") {
                result.tokens.isEmpty() shouldBe true
            }
        }
    }
})
