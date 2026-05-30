package com.sportsapp.application.mcp

import com.sportsapp.domain.mcp.McpAnomalyEvent
import com.sportsapp.domain.mcp.McpAnomalyEventDomainService
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
