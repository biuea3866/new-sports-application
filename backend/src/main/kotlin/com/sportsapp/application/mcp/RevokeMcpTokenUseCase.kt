package com.sportsapp.application.mcp

import com.sportsapp.domain.mcp.McpTokenDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RevokeMcpTokenUseCase(
    private val mcpTokenDomainService: McpTokenDomainService,
) {
    @Transactional
    fun execute(command: RevokeMcpTokenCommand) {
        mcpTokenDomainService.revokeToken(
            tokenId = command.tokenId,
            requesterId = command.requesterId,
        )
    }
}
