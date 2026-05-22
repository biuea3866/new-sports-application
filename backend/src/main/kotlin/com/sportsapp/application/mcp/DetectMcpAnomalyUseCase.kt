package com.sportsapp.application.mcp

import com.sportsapp.domain.mcp.McpAnomalyDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DetectMcpAnomalyUseCase(
    private val mcpAnomalyDomainService: McpAnomalyDomainService,
) {
    @Transactional(readOnly = true)
    fun execute() {
        mcpAnomalyDomainService.detectAll()
    }
}
