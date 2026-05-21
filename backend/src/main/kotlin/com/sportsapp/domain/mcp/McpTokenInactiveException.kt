package com.sportsapp.domain.mcp

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class McpTokenInactiveException(tokenId: Long, status: McpTokenStatus) : BusinessException(
    errorCode = "MCP_TOKEN_INACTIVE",
    message = "McpToken(id=$tokenId) is not active: current status=$status",
) {
    override val status: ErrorStatus = ErrorStatus.FORBIDDEN
}
