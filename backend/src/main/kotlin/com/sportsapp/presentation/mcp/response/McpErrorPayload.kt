package com.sportsapp.presentation.mcp.response

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class McpErrorPayload(
    val code: String,
    val message: String,
    val recoverable: Boolean,
    val suggestedAction: String? = null,
    val suggestedParams: Map<String, Any>? = null,
)
