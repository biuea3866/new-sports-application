package com.sportsapp.application.mcp.usecase
import com.sportsapp.application.mcp.dto.McpAuditLogResponse
import com.sportsapp.application.mcp.dto.ListMcpAuditLogsResponse
import com.sportsapp.application.mcp.dto.ListMcpAuditLogsCommand

import com.sportsapp.domain.mcp.service.McpAuditLogDomainService
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListMcpAuditLogsUseCase(
    private val mcpAuditLogDomainService: McpAuditLogDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(command: ListMcpAuditLogsCommand): ListMcpAuditLogsResponse {
        val pageable = PageRequest.of(command.page, command.size, Sort.by(Sort.Direction.DESC, "calledAt"))
        val page = mcpAuditLogDomainService.listByUser(
            userId = command.userId,
            from = command.startCalledAt,
            to = command.endCalledAt,
            pageable = pageable,
        )
        return ListMcpAuditLogsResponse.of(page)
    }
}
