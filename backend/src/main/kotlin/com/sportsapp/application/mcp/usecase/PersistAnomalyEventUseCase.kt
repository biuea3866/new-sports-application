package com.sportsapp.application.mcp.usecase
import com.sportsapp.application.mcp.dto.McpAnomalyEventResponse
import com.sportsapp.application.mcp.dto.PersistAnomalyEventCommand

import com.sportsapp.domain.mcp.entity.McpAnomalyEvent
import com.sportsapp.domain.mcp.service.McpAnomalyEventDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PersistAnomalyEventUseCase(
    private val mcpAnomalyEventDomainService: McpAnomalyEventDomainService,
) {
    @Transactional
    fun execute(command: PersistAnomalyEventCommand): McpAnomalyEventResponse? {
        val persisted = mcpAnomalyEventDomainService.persistIfNotDuplicate(
            McpAnomalyEvent(
                sourceEventId = command.sourceEventId,
                tokenId = command.tokenId,
                ownerUserId = command.ownerUserId,
                detectedAt = command.detectedAt,
                currentHourCount = command.currentHourCount,
                baselineAverage = command.baselineAverage,
            )
        ) ?: return null
        return McpAnomalyEventResponse.of(persisted)
    }
}
