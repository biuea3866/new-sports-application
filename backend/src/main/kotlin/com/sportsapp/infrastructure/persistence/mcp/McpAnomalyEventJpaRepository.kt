package com.sportsapp.infrastructure.persistence.mcp

import com.sportsapp.domain.mcp.McpAnomalyEvent
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface McpAnomalyEventJpaRepository : JpaRepository<McpAnomalyEvent, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): McpAnomalyEvent?
    fun findByIdAndOwnerUserIdAndDeletedAtIsNull(id: Long, ownerUserId: Long): McpAnomalyEvent?
    fun findAllByOwnerUserIdAndDeletedAtIsNull(ownerUserId: Long, pageable: Pageable): Page<McpAnomalyEvent>
    fun existsBySourceEventId(sourceEventId: String): Boolean
}
