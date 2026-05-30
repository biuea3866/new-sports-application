package com.sportsapp.domain.mcp

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class McpAnomalyEventNotOwnedException(anomalyEventId: Long) : BusinessException(
    errorCode = "MCP_ANOMALY_EVENT_NOT_OWNED",
    message = "McpAnomalyEvent(id=$anomalyEventId) is not owned by the requesting user",
) {
    override val status: ErrorStatus = ErrorStatus.FORBIDDEN
}
