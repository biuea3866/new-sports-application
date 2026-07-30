package com.sportsapp.application.mcp.usecase
import com.sportsapp.application.mcp.dto.McpAnomalyEventResponse
import com.sportsapp.application.mcp.dto.MarkAnomalyFalsePositiveCommand

import com.sportsapp.domain.mcp.service.McpAnomalyEventDomainService
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
