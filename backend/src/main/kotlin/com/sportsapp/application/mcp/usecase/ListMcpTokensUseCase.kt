package com.sportsapp.application.mcp.usecase
import com.sportsapp.application.mcp.dto.ListMcpTokensResponse

import com.sportsapp.domain.mcp.service.McpTokenDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListMcpTokensUseCase(
    private val mcpTokenDomainService: McpTokenDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(userId: Long): ListMcpTokensResponse {
        val tokens = mcpTokenDomainService.listMyTokens(userId)
        return ListMcpTokensResponse.of(tokens)
    }
}
