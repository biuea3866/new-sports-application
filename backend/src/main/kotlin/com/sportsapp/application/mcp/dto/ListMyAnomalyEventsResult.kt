package com.sportsapp.application.mcp.dto

import com.sportsapp.domain.mcp.entity.McpAnomalyEvent
import org.springframework.data.domain.Page

data class ListMyAnomalyEventsResponse(
    val content: List<McpAnomalyEventResponse>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int,
) {
    companion object {
        fun of(page: Page<McpAnomalyEvent>): ListMyAnomalyEventsResponse = ListMyAnomalyEventsResponse(
            content = page.content.map { McpAnomalyEventResponse.of(it) },
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            page = page.number,
            size = page.size,
        )
    }
}
