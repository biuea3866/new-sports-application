package com.sportsapp.domain.mcp.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class McpScopeNotFoundException(scopeName: String) : BusinessException(
    errorCode = "MCP_SCOPE_NOT_FOUND",
    message = "MCP scope not found: $scopeName",
) {
    override val status: ErrorStatus = ErrorStatus.BAD_REQUEST
}
