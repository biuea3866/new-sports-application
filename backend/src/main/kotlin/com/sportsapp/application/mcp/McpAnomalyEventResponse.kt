package com.sportsapp.application.mcp

import com.sportsapp.domain.mcp.McpAnomalyEvent
import com.sportsapp.domain.mcp.McpAnomalyEventStatus
import java.time.ZonedDateTime

data class McpAnomalyEventResponse(
    val id: Long,
    val tokenId: Long,
    val ownerUserId: Long,
    val detectedAt: ZonedDateTime,
    val currentHourCount: Long,
    val baselineAverage: Double,
    val status: McpAnomalyEventStatus,
    val falsePositive: Boolean,
    val resolvedAt: ZonedDateTime?,
    val note: String?,
) {
    companion object {
        fun of(entity: McpAnomalyEvent): McpAnomalyEventResponse = McpAnomalyEventResponse(
            id = entity.id,
            tokenId = entity.tokenId,
            ownerUserId = entity.ownerUserId,
            detectedAt = entity.detectedAt,
            currentHourCount = entity.currentHourCount,
            baselineAverage = entity.baselineAverage,
            status = entity.status,
            falsePositive = entity.falsePositive,
            resolvedAt = entity.resolvedAt,
            note = entity.note,
        )
    }
}
