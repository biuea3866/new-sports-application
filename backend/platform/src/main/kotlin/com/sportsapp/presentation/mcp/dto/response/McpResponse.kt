package com.sportsapp.presentation.mcp.dto.response

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class McpResponse<T>(
    val status: McpResponseStatus,
    val data: T? = null,
    val error: McpErrorPayload? = null,
    val pagination: McpPagination? = null,
) {
    companion object {
        fun <T> ok(data: T, pagination: McpPagination? = null): McpResponse<T> =
            McpResponse(
                status = McpResponseStatus.OK,
                data = data,
                pagination = pagination,
            )

        fun error(payload: McpErrorPayload): McpResponse<Nothing> =
            McpResponse(
                status = McpResponseStatus.ERROR,
                error = payload,
            )

        fun confirmRequired(data: Map<String, Any>): McpResponse<Map<String, Any>> =
            McpResponse(
                status = McpResponseStatus.CONFIRM_REQUIRED,
                data = data,
            )
    }
}

enum class McpResponseStatus {
    OK,
    ERROR,
    CONFIRM_REQUIRED,
}
