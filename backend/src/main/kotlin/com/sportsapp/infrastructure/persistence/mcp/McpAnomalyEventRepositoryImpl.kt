package com.sportsapp.infrastructure.persistence.mcp

import com.sportsapp.domain.mcp.McpAnomalyEvent
import com.sportsapp.domain.mcp.McpAnomalyEventRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository

@Repository
class McpAnomalyEventRepositoryImpl(
    private val mcpAnomalyEventJpaRepository: McpAnomalyEventJpaRepository,
) : McpAnomalyEventRepository {

    override fun save(mcpAnomalyEvent: McpAnomalyEvent): McpAnomalyEvent =
        mcpAnomalyEventJpaRepository.save(mcpAnomalyEvent)

    override fun findById(id: Long): McpAnomalyEvent? =
        mcpAnomalyEventJpaRepository.findByIdAndDeletedAtIsNull(id)

    override fun findByIdAndOwnerUserId(id: Long, ownerUserId: Long): McpAnomalyEvent? =
        mcpAnomalyEventJpaRepository.findByIdAndOwnerUserIdAndDeletedAtIsNull(id, ownerUserId)

    override fun findByOwnerUserId(ownerUserId: Long, pageable: Pageable): Page<McpAnomalyEvent> =
        mcpAnomalyEventJpaRepository.findAllByOwnerUserIdAndDeletedAtIsNull(ownerUserId, pageable)

    override fun existsBySourceEventId(sourceEventId: String): Boolean =
        mcpAnomalyEventJpaRepository.existsBySourceEventId(sourceEventId)
}
