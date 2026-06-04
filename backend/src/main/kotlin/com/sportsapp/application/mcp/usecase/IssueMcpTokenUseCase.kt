package com.sportsapp.application.mcp.usecase
import com.sportsapp.application.mcp.dto.IssueMcpTokenResponse
import com.sportsapp.application.mcp.dto.IssueMcpTokenUseCaseCommand

import com.sportsapp.domain.mcp.service.McpTokenDomainService
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
