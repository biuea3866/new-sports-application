package com.sportsapp.infrastructure.config

import com.sportsapp.presentation.mcp.toolregistry.McpBookingTools
import com.sportsapp.presentation.mcp.toolregistry.McpFacilityTools
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
 */
@Configuration
@Profile("!test-jpa")
class McpToolRegistryConfig {

    @Bean
    fun mcpReadToolCallbackProvider(
        mcpFacilityTools: McpFacilityTools,
        mcpBookingTools: McpBookingTools,
    ): ToolCallbackProvider =
        MethodToolCallbackProvider.builder()
            .toolObjects(mcpFacilityTools, mcpBookingTools)
            .build()
}
