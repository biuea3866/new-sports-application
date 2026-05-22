package com.sportsapp.infrastructure.config

import com.sportsapp.presentation.mcp.toolregistry.McpBookingTools
import com.sportsapp.presentation.mcp.toolregistry.McpFacilityStatsTools
import com.sportsapp.presentation.mcp.toolregistry.McpFacilityTools
import com.sportsapp.presentation.mcp.toolregistry.McpNotificationTools
import com.sportsapp.presentation.mcp.toolregistry.McpOperatorProfileTools
import org.springframework.ai.tool.ToolCallbackProvider
import org.springframework.ai.tool.method.MethodToolCallbackProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * MCP tool registry 설정.
 *
 * Spring AI MCP Server 에 read tool 을 등록한다.
 * - getFacilities: read:facility scope
 * - getBookings: read:booking scope
 * - getFacilityStats: read:facility:stats scope
 * - getOperatorProfile: read:operator:profile scope
 * - getNotifications: read:notification scope
 */
@Configuration
@Profile("!test-jpa")
class McpToolRegistryConfig {

    @Bean
    fun mcpReadToolCallbackProvider(
        mcpFacilityTools: McpFacilityTools,
        mcpBookingTools: McpBookingTools,
        mcpFacilityStatsTools: McpFacilityStatsTools,
        mcpOperatorProfileTools: McpOperatorProfileTools,
        mcpNotificationTools: McpNotificationTools,
    ): ToolCallbackProvider =
        MethodToolCallbackProvider.builder()
            .toolObjects(
                mcpFacilityTools,
                mcpBookingTools,
                mcpFacilityStatsTools,
                mcpOperatorProfileTools,
                mcpNotificationTools,
            )
            .build()
}
