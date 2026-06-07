package com.sportsapp.domain.mcp.gateway
import com.sportsapp.domain.mcp.dto.ConfirmationTokenContext

import java.time.Duration

interface ConfirmationTokenGateway {
    fun issue(context: ConfirmationTokenContext, ttl: Duration = Duration.ofMinutes(5)): String
    fun consume(token: String): ConfirmationTokenContext
}
