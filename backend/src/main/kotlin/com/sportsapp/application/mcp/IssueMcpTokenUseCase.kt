package com.sportsapp.application.mcp

import com.sportsapp.domain.mcp.McpTokenDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class IssueMcpTokenUseCase(
    private val mcpTokenDomainService: McpTokenDomainService,
) {
    @Transactional
    fun execute(command: IssueMcpTokenUseCaseCommand): IssueMcpTokenResponse {
        val result = mcpTokenDomainService.issueToken(command.toDomainCommand())
        return IssueMcpTokenResponse.of(result)
    }
}
