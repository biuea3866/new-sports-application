package com.sportsapp.domain.mcp.repository

import com.sportsapp.domain.mcp.entity.McpAnomalyEvent
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface McpAnomalyEventRepository {
    fun save(mcpAnomalyEvent: McpAnomalyEvent): McpAnomalyEvent
    fun findById(id: Long): McpAnomalyEvent?
    fun findByIdAndOwnerUserId(id: Long, ownerUserId: Long): McpAnomalyEvent?
    fun findByOwnerUserId(ownerUserId: Long, pageable: Pageable): Page<McpAnomalyEvent>
    fun existsBySourceEventId(sourceEventId: String): Boolean
}
