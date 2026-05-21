package com.sportsapp.application.mcp

import com.sportsapp.domain.mcp.McpTokenDomainService
import com.sportsapp.domain.mcp.McpTokenNotOwnedException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify

class RevokeMcpTokenUseCaseTest : BehaviorSpec({

    val mcpTokenDomainService = mockk<McpTokenDomainService>()
    val useCase = RevokeMcpTokenUseCase(mcpTokenDomainService)

    Given("[U-04] 자신의 토큰 ID로 폐기를 요청하면") {
        justRun { mcpTokenDomainService.revokeToken(tokenId = 10L, requesterId = 1L) }

        When("execute를 호출하면") {
            useCase.execute(RevokeMcpTokenCommand(tokenId = 10L, requesterId = 1L))

            Then("[U-04] DomainService의 revokeToken이 1회 호출된다") {
                verify(exactly = 1) { mcpTokenDomainService.revokeToken(tokenId = 10L, requesterId = 1L) }
            }
        }
    }

    Given("[U-05] 타인 소유 토큰 ID로 폐기를 요청하면") {
        every {
            mcpTokenDomainService.revokeToken(tokenId = 10L, requesterId = 99L)
        } throws McpTokenNotOwnedException(tokenId = 10L)

        When("execute를 호출하면") {
            Then("[U-05] McpTokenNotOwnedException이 전파된다") {
                shouldThrow<McpTokenNotOwnedException> {
                    useCase.execute(RevokeMcpTokenCommand(tokenId = 10L, requesterId = 99L))
                }
            }
        }
    }
})
