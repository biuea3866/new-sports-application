package com.sportsapp.application.mcp

import com.sportsapp.domain.mcp.McpAuditLogDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetMcpUsageAnalyticsUseCase(
    private val mcpAuditLogDomainService: McpAuditLogDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(command: GetMcpUsageAnalyticsCommand): GetMcpUsageAnalyticsResponse {
        val result = mcpAuditLogDomainService.aggregateUsageAnalytics(
            userId = command.userId,
            from = command.from,
            to = command.to,
        )
        return GetMcpUsageAnalyticsResponse.of(result)
    }
}
