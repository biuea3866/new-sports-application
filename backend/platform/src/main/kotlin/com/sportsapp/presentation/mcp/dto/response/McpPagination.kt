package com.sportsapp.presentation.mcp.dto.response

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class McpPagination(
    val page: Int,
    val size: Int,
    val total: Long,
    val hasNext: Boolean,
) {
    companion object {
        fun of(page: Int, size: Int, total: Long): McpPagination =
            McpPagination(
                page = page,
                size = size,
                total = total,
                hasNext = (page.toLong() + 1) * size < total,
            )
    }
}
