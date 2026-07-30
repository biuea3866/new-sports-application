package com.sportsapp.application.mcp.usecase
import com.sportsapp.application.mcp.dto.GetMcpUsageAnalyticsResponse
import com.sportsapp.application.mcp.dto.GetMcpUsageAnalyticsCommand

import com.sportsapp.domain.mcp.service.McpAuditLogDomainService
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
