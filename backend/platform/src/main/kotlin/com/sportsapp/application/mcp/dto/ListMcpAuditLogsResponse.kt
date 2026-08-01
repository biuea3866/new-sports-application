package com.sportsapp.application.mcp.dto

import com.sportsapp.domain.mcp.entity.McpAuditLog
import org.springframework.data.domain.Page

data class ListMcpAuditLogsResponse(
    val content: List<McpAuditLogResponse>,
    val totalElements: Long,
    val totalPages: Int,
    val pageNumber: Int,
    val pageSize: Int,
) {
    companion object {
        fun of(page: Page<McpAuditLog>): ListMcpAuditLogsResponse =
            ListMcpAuditLogsResponse(
                content = page.content.map { McpAuditLogResponse.of(it) },
                totalElements = page.totalElements,
                totalPages = page.totalPages,
                pageNumber = page.number,
                pageSize = page.size,
            )
    }
}
