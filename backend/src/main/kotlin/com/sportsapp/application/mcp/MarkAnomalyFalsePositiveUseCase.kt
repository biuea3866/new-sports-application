package com.sportsapp.application.mcp

import com.sportsapp.domain.mcp.McpAnomalyEventDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MarkAnomalyFalsePositiveUseCase(
    private val mcpAnomalyEventDomainService: McpAnomalyEventDomainService,
) {
    @Transactional
    fun execute(command: MarkAnomalyFalsePositiveCommand): McpAnomalyEventResponse {
        val anomalyEvent = mcpAnomalyEventDomainService.getByIdAndOwnerUserId(
            id = command.anomalyEventId,
            ownerUserId = command.requestUserId,
        )
        anomalyEvent.markFalsePositive(command.requestUserId, command.note)
        val saved = mcpAnomalyEventDomainService.persist(anomalyEvent)
        return McpAnomalyEventResponse.of(saved)
    }
}
