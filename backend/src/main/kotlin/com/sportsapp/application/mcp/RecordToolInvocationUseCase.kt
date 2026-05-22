package com.sportsapp.application.mcp

import com.sportsapp.domain.mcp.McpAuditLog
import com.sportsapp.domain.mcp.McpAuditLogDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RecordToolInvocationUseCase(
    private val mcpAuditLogDomainService: McpAuditLogDomainService,
) {
    @Transactional
    fun execute(command: RecordToolInvocationCommand) {
        val auditLog = McpAuditLog(
            tokenId = command.tokenId,
            userId = command.userId,
            toolName = command.toolName,
            paramsMasked = command.paramsMasked,
            statusCode = command.statusCode,
            latencyMs = command.latencyMs,
            clientUserAgent = command.clientUserAgent,
            ipAddr = command.ipAddr,
            asn = null,
            calledAt = command.calledAt,
        )
        mcpAuditLogDomainService.recordToolInvocation(auditLog)
    }
}
