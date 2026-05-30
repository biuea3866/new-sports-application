package com.sportsapp.domain.mcp

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

@Service
class McpAnomalyEventDomainService(
    private val mcpAnomalyEventRepository: McpAnomalyEventRepository,
) {
    fun persist(mcpAnomalyEvent: McpAnomalyEvent): McpAnomalyEvent =
        mcpAnomalyEventRepository.save(mcpAnomalyEvent)

    fun persistIfNotDuplicate(mcpAnomalyEvent: McpAnomalyEvent): McpAnomalyEvent? {
        if (mcpAnomalyEventRepository.existsBySourceEventId(mcpAnomalyEvent.sourceEventId)) return null
        return mcpAnomalyEventRepository.save(mcpAnomalyEvent)
    }

    fun listByOwner(ownerUserId: Long, pageable: Pageable): Page<McpAnomalyEvent> =
        mcpAnomalyEventRepository.findByOwnerUserId(ownerUserId, pageable)

    fun getById(id: Long): McpAnomalyEvent =
        mcpAnomalyEventRepository.findById(id)
            ?: throw ResourceNotFoundException("McpAnomalyEvent", id)

    fun getByIdAndOwnerUserId(id: Long, ownerUserId: Long): McpAnomalyEvent =
        mcpAnomalyEventRepository.findByIdAndOwnerUserId(id, ownerUserId)
            ?: throw ResourceNotFoundException("McpAnomalyEvent", id)
}
