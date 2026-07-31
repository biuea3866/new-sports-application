package com.sportsapp.domain.mcp.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus
import java.time.ZonedDateTime

class McpTokenExpiredException(tokenId: Long, expiresAt: ZonedDateTime) : BusinessException(
    errorCode = "MCP_TOKEN_EXPIRED",
    message = "McpToken(id=$tokenId) expired at $expiresAt",
) {
    override val status: ErrorStatus = ErrorStatus.UNAUTHORIZED
}
