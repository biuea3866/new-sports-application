package com.sportsapp.application.mcp

import com.sportsapp.domain.mcp.McpAnomalyEventDomainService
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListMyAnomalyEventsUseCase(
    private val mcpAnomalyEventDomainService: McpAnomalyEventDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(command: ListMyAnomalyEventsCommand): ListMyAnomalyEventsResponse {
        val pageable = PageRequest.of(command.page, command.size, Sort.by(Sort.Direction.DESC, "detectedAt"))
        val page = mcpAnomalyEventDomainService.listByOwner(command.ownerUserId, pageable)
        return ListMyAnomalyEventsResponse.of(page)
    }
}
