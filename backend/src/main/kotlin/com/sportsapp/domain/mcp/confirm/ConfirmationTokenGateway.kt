package com.sportsapp.domain.mcp.confirm

import java.time.Duration

interface ConfirmationTokenGateway {
    fun issue(context: ConfirmationTokenContext, ttl: Duration = Duration.ofMinutes(5)): String
    fun consume(token: String): ConfirmationTokenContext
}
