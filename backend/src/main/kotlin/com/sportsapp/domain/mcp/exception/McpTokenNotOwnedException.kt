package com.sportsapp.domain.mcp.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class McpTokenNotOwnedException(tokenId: Long) : BusinessException(
    errorCode = "MCP_TOKEN_NOT_OWNED",
    message = "McpToken(id=$tokenId) is not owned by the requester",
) {
    override val status: ErrorStatus = ErrorStatus.FORBIDDEN
}
