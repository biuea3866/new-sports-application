package com.sportsapp.application.mcp.usecase

import com.sportsapp.application.mcp.dto.VerifyMcpTokenCommand
import com.sportsapp.application.mcp.dto.VerifyMcpTokenResponse
import com.sportsapp.domain.mcp.service.McpTokenDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class VerifyMcpTokenUseCase(
    private val mcpTokenDomainService: McpTokenDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(command: VerifyMcpTokenCommand): VerifyMcpTokenResponse =
        VerifyMcpTokenResponse.of(mcpTokenDomainService.verifyToken(command.plainToken))
}
