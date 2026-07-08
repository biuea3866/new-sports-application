package com.sportsapp.application.mcp.dto

import com.sportsapp.domain.mcp.event.McpAnomalyDetectedEvent
import java.time.ZonedDateTime

data class PersistAnomalyEventCommand(
    val sourceEventId: String,
    val tokenId: Long,
    val ownerUserId: Long,
    val detectedAt: ZonedDateTime,
    val currentHourCount: Long,
    val baselineAverage: Double,
) {
    companion object {
        fun of(event: McpAnomalyDetectedEvent): PersistAnomalyEventCommand = PersistAnomalyEventCommand(
            sourceEventId = event.eventId,
            tokenId = event.tokenId,
            ownerUserId = event.userId,
            detectedAt = event.occurredAt,
            currentHourCount = event.currentHourCount,
            baselineAverage = event.baselineAverage,
        )
    }
}
