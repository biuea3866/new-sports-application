package com.sportsapp.presentation.mcp.controller

import com.sportsapp.domain.mcp.vo.McpAuthenticatedPrincipal
import com.sportsapp.domain.mcp.exception.ConfirmationParamsMismatchException
import com.sportsapp.domain.mcp.dto.ConfirmationTokenContext
import com.sportsapp.domain.mcp.gateway.ConfirmationTokenGateway
import com.sportsapp.presentation.mcp.dto.response.McpResponse
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder

/**
 * MCP Write tool 공통 베이스.
 * issueConfirmation / validateHashAndConsume / resolveCallerUserId 를 한 곳에서 관리한다.
 */
abstract class McpWriteToolBase(
    protected val confirmationTokenGateway: ConfirmationTokenGateway,
) {

    protected fun issueConfirmation(
        toolName: String,
        userId: Long,
        paramsHash: String,
        metadata: Map<String, Any>,
    ): McpResponse<Map<String, Any>> {
        val token = confirmationTokenGateway.issue(
            ConfirmationTokenContext(toolName = toolName, userId = userId, paramsHash = paramsHash)
        )
        return McpResponse.confirmRequired(data = metadata + ("confirmationToken" to token))
    }

    protected fun validateHashAndConsume(confirmationToken: String, expectedHash: String) {
        val context = confirmationTokenGateway.consume(confirmationToken)
        if (context.paramsHash != expectedHash) throw ConfirmationParamsMismatchException(confirmationToken)
    }

    protected fun resolveCallerUserId(): Long {
        val principal = SecurityContextHolder.getContext().authentication?.principal as? McpAuthenticatedPrincipal
            ?: throw AccessDeniedException("MCP authentication required")
        return principal.userId
    }
}
